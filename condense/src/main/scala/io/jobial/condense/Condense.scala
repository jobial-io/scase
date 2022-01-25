/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.condense

import cats.effect.IO
import com.amazonaws.services.cloudformation.model.{AlreadyExistsException, DeleteStackResult}
import com.monsanto.arch.cloudformation.model.Template
import io.jobial.condense.aws.client.{AwsContext, CloudformationClient, ConfigurationUtils, Hash, S3Client, StsClient}
import org.apache.commons.io.IOUtils

import java.io.{File, FileInputStream}
//import io.jobial.scase.cloudformation.ScaseCloudformation.{command, createChangeSetAndWaitForComplete, createStack, deleteStack, fromTry, httpsUrl, opt, param, s3PutText, subcommand, subcommands, updateStack}
import io.jobial.sclap.CommandLineApp
import spray.json._
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import collection.JavaConverters._

object Condense extends CommandLineApp with CloudformationClient with S3Client with ConfigurationUtils {

  val awsContext = AwsContext()
  
  import awsContext.stsClient._
  
  def run =
    command
      .header("Condense")
      .description("Tool for managing AWS resources through Cloudformation templates.") {
        for {
          stackClassName <- param[String].required.label("stack_class")
            .description("The fully qualified stack class name")
          stackName <- opt[String]("stack-name")
            .description("Optional stack name")
          region <- opt[String]("region")
            .description("The AWS region where the stack and its resources are created, unless specified otherwise")
            .default(getDefaultRegion.getOrElse("eu-west-1"))
          label <- opt[String]("label")
            .description("An additional discriminator label for the stack where applicable")
          s3Bucket <- opt[String]("s3-bucket")
            .description("The s3 bucket to use for the stack template and other resources")
          s3Prefix <- opt[String]("s3-prefix")
            .description("The s3 key prefix to use for the stack template and other resources")
          //accountId <- noSpec(getAccountId)
          dockerImageTags <- opt[String]("docker-image-tags")
            .description("Mapping of docker images to tags, in the format <image_name>:<tag>,... . " +
              "By default, the stack will use the latest tag for images. This option allows to override this.")
          printOnly <- opt[Boolean]("print-only")
            .description("Print the generated template and operations, do not make any changes")
            .default(false)
          lambdaFile <- opt[File]("lambda-file")
          context =
            for {
              accountId <- getAccountId
              bucketName = s3Bucket.getOrElse(s"cloudformation-$accountId")
              _ <- s3CreateBucket(bucketName, region).map { _ =>
                println(s"created bucket $bucketName")
              }.handleErrorWith { _ =>
                IO(println(s"bucket $bucketName already exists"))
              }
              prefix = s3Prefix.getOrElse("")
            } yield StackContext(
              stackName.getOrElse(stackClassName.substring(stackClassName.lastIndexOf(".") + 1).replaceAll("\\$", "")),
              stackClassName,
              region,
              bucketName,
              prefix,
              lambdaFile,
              None,
              label,
              dockerImageTags.map {
                _.split(",").map { p =>
                  val l = p.split(":")
                  (l(0), l(1))
                }.toMap
              },
              printOnly
            )
          subcommandResult <- subcommands(
            createOrUpdateStackSubcommand(context),
            createStackSubcommand(context),
            updateStackSubcommand(context),
            deleteStackSubcommand(context)
          )
        } yield subcommandResult
      }
  
  def uploadLambdaFile(context: StackContext) =
    context.lambdaFile match {
      case Some(lambdaFile) =>
        val separator = if (context.s3Prefix.isEmpty || context.s3Prefix.endsWith("/")) "" else "/"
        println(s"uploading lambda file $lambdaFile")
        val bytes = IOUtils.toByteArray(new FileInputStream(lambdaFile))
        val hash = Hash.hash(bytes.mkString)
        val s3Key = s"${context.s3Prefix}${separator}${lambdaFile.getName}.$hash"
        for {
          _ <- s3PutObject(context.s3Bucket, s3Key, bytes)
        } yield context.copy(lambdaFileS3Key = Some(s3Key))
      case None =>
        IO(context)
    }

  def createOrUpdateStackSubcommand(context: IO[StackContext]) =
    subcommand("create-or-update")
      .header("Create a new stack or update if exists.")
      .description("This command creates a new stack based on the specified stack name, level and optional label.") {
        (for {
          context <- context
          context <- uploadLambdaFile(context)
          template <- context.template
          stack <-
            if (context.printOnly)
              IO(println(template.toJson.prettyPrint))
            else
              createStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint)).handleErrorWith {
                case t: AlreadyExistsException =>
                  updateStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
              }
          _ <- IO(println(s"creating or updating stack ${context.stackName} from $template"))
        } yield stack) handleErrorWith { t =>
          t.printStackTrace
          IO()
        }
      }

  def createStackSubcommand(context: IO[StackContext]) =
    subcommand("create")
      .header("Create a new stack.")
      .description("This command creates a new stack based on the specified stack name, level and optional label.") {
        for {
          context <- context
          template <- context.template
          stack <-
            if (context.printOnly)
              IO(println(template.toJson.prettyPrint))
            else {
              println(s"creating stack ${context.stackName} from $template")
              createStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
            }
        } yield stack
      }

  case class NotUpdatingStack(stackName: String) extends RuntimeException(s"updates to stack $stackName will not be applied")

  def updateStackSubcommand(context: IO[StackContext]) =
    subcommand("update")
      .header("Update an existing stack.")
      .description("This command updates an existing stack based on the specified stack name, level and optional label.") {
        for {
          context <- context
          template <- context.template
          stack <-
            if (context.printOnly)
              IO(println(template.toJson.prettyPrint))
            else {
              println(s"generating changeset for stack ${context.stackName}")
              for {
                changeSet <- createChangeSetAndWaitForComplete(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
                result <- {
                  println(changeSet)
                  val changes = changeSet.getChanges.asScala
                  if (changes.isEmpty)
                    IO(println("nothing to change"))
                  else {
                    println("\nThe following changes will be applied:\n")
                    for {
                      c <- changes
                    } println(s"${c.getResourceChange.getAction} ${c.getResourceChange.getLogicalResourceId} ${c.getResourceChange.getResourceType} ${c.getResourceChange.getReplacement}")
                    println

                    if (readLine("Proceed with the update? ").equalsIgnoreCase("y")) {
                      println(s"updating stack ${context.stackName}")
                      updateStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
                    } else {
                      IO.raiseError(NotUpdatingStack(context.stackName))
                    }
                  }
                }
              } yield result
            }
        } yield stack
      }

  def deleteStackWithCleanup(context: StackContext) =
    for {
      // TODO: add resource cleanup
      _ <- IO(println(s"deleting stack ${context.stackName}"))
      r <- deleteStack(context.stackName)
    } yield r

  case object CannotDeleteProdStack extends IllegalStateException(s"cannot delete prod stack")

  def deleteStackSubcommand(context: IO[StackContext]) =
    subcommand("delete")
      .header("Delete an existing stack.")
      .description("This command deletes an existing stack based on the specified stack name, level and optional label.") {
        for {
          context <- context
          r <- deleteStackWithCleanup(context)
        } yield r
      }

  case object LevelMustBeSpecified extends IllegalStateException(s"level must be specified for this stack")

}
