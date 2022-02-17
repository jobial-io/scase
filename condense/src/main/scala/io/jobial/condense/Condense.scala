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
import com.amazonaws.services.cloudformation.model.AlreadyExistsException
import io.jobial.scase.aws.client._
import io.jobial.scase.util.Hash
import io.jobial.sclap.CommandLineApp
import org.apache.commons.io.IOUtils
import spray.json._

import java.io.{File, FileInputStream}
import scala.Console._
import scala.collection.JavaConverters._
import scala.io.StdIn.readLine

object Condense extends CommandLineApp with CloudformationClient with S3Client with StsClient with ConfigurationUtils {

  // TODO: move common options under subcommands
  def run =
    command
      .header("Condense")
      .description("Scala tool for managing AWS resources through Cloudformation.") {
        for {
          stackClassName <- param[String].required.label("<stack-class>")
            .description("Fully qualified name of class implementing the CloudformationStack trait")
          stackName <- opt[String]("stack-name")
            .description("Optional stack name")
          region <- opt[String]("region")
            .description("The AWS region where the stack and its resources are created, unless specified otherwise")
            .default(getDefaultRegion.getOrElse("eu-west-1"))
          label <- opt[String]("label")
            .description("An optional discriminator label for the stack (e.g. prod or test)")
          s3Bucket <- opt[String]("s3-bucket")
            .description("S3 bucket to use for the stack template and other resources")
          s3Prefix <- opt[String]("s3-prefix")
            .description("S3 key prefix to use for the stack template and other resources")
          dockerImageTags <- opt[String]("docker-image-tags")
            .description("Mapping of docker images to tags, in the format <image_name>:<tag>,... . " +
              "By default, the stack will use the latest tag for images. This option allows to override the default.")
          printOnly <- opt[Boolean]("print-only")
            .description("Print the generated template and operations, do not make any changes")
            .default(false)
          lambdaFile <- opt[File]("lambda-file")
            .description("Local Zip or Jar file with code for lambda functions")
          context =
            for {
              accountId <- getAccountId
              bucketName = s3Bucket.getOrElse(s"cloudformation-$accountId")
              _ <- s3CreateBucket(bucketName, region).flatMap { _ =>
                message(s"created bucket $bucketName")
              }.handleErrorWith { _ =>
                message(s"bucket $bucketName already exists")
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

  val awsContext = AwsContext()

  def uploadLambdaFile(context: StackContext) =
    context.lambdaFile match {
      case Some(lambdaFile) =>
        val separator = if (context.s3Prefix.isEmpty || context.s3Prefix.endsWith("/")) "" else "/"
        val bytes = IOUtils.toByteArray(new FileInputStream(lambdaFile))
        val hash = Hash.hash(bytes.mkString)
        val s3Key = s"${context.s3Prefix}${separator}${lambdaFile.getName}.$hash"
        for {
          exists <- s3Exists(context.s3Bucket, s3Key)
          _ <- if (exists)
            message("lambda file already up to date")
          else
            for {
              _ <- message(s"uploading lambda file $lambdaFile")
              r <- s3PutObject(context.s3Bucket, s3Key, bytes)
            } yield r
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
              message(template.toJson.prettyPrint)
            else
              createStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint)).handleErrorWith {
                case t: AlreadyExistsException =>
                  updateStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
              }
          _ <- message(s"creating or updating stack ${context.stackName} from $template")
        } yield stack) handleErrorWith { t =>
          IO(t.printStackTrace)
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
              message(template.toJson.prettyPrint)
            else
              for {
                _ <- message(s"creating stack ${context.stackName} from $template")
                r <- createStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
              } yield r
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
              message(template.toJson.prettyPrint)
            else
              for {
                _ <- message(s"generating changeset for stack ${context.stackName}")
                changeSet <- createChangeSetAndWaitForComplete(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
                _ <- message(changeSet.toString)
                result <- {
                  val changes = changeSet.getChanges.asScala
                  if (changes.isEmpty)
                    message("nothing to change")
                  else for {
                    _ <- message("\nThe following changes will be applied:\n")
                    r <- {
                      for {
                        c <- changes
                      } println(s"${c.getResourceChange.getAction} ${c.getResourceChange.getLogicalResourceId} ${c.getResourceChange.getResourceType} ${c.getResourceChange.getReplacement}")
                      println

                      if (readLine("Proceed with the update? ").equalsIgnoreCase("y"))
                        for {
                          _ <- message(s"updating stack ${context.stackName}")
                          r <- updateStack(context.stackName, None, templateBody = Some(template.toJson.prettyPrint))
                        } yield r
                      else {
                        IO.raiseError(NotUpdatingStack(context.stackName))
                      }
                    }
                  } yield r
                }
              } yield result
        } yield stack
      }

  def message(s: String) =
    IO(println(s"${BLUE}$s${RESET}"))

  def deleteStackWithCleanup(context: StackContext) =
    for {
      // TODO: add resource cleanup
      _ <- message(s"deleting stack ${context.stackName}")
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
