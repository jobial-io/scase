package io.jobial.scase.cloudformation

import cats.effect.IO
import com.amazonaws.services.cloudformation.model.DeleteStackResult
import com.monsanto.arch.cloudformation.model.Template
import io.jobial.scase.aws.client.{CloudformationClient, StsClient}
//import io.jobial.scase.cloudformation.ScaseCloudformation.{command, createChangeSetAndWaitForComplete, createStack, deleteStack, fromTry, httpsUrl, opt, param, s3PutText, subcommand, subcommands, updateStack}
import io.jobial.sclap.CommandLineApp
import spray.json._
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import collection.JavaConverters._


trait CloudformationStackApp extends CommandLineApp with CloudformationClient with StsClient with CloudformationStack {

  def run =
    command
      .header("Scase AWS Tool")
      .description("Tool for managing Cloudtemp AWS resources and generating Cloudformation templates.") {
        for {
          stackName <- opt[String]("stack")
            .description("The name of the stack")
          region <- opt[String]("region")
            .description("The AWS region where the stack and its resources are created, unless specified otherwise")
            .default(getDefaultRegion.getOrElse("eu-west-1"))
          label <- opt[String]("label")
            .description("An additional discriminator label for the stack where applicable")
          stackS3Bucket <- opt[String]("stack-s3-bucket")
            .description("The s3 bucket to use for the stack template and other resources")
          stackS3Prefix <- opt[String]("stack-s3-prefix")
            .description("The s3 key prefix to use for the stack template and other resources")
          // TODO: add implicit for this
          accountId <- noSpec(getAccount)
          dockerImageTags <- opt[String]("docker-image-tags")
            .description("Mapping of docker images to tags, in the format <image_name>:<tag>,... . " +
              "By default, the stack will use the latest tag for images. This option allows to override this.")
          printOnly <- opt[Boolean]("print-only")
            .description("Print the generated template and operations, do not make any changes")
            .default(false)
          context = StackContext(
            stackName.getOrElse(getClass.getSimpleName.replaceAll("\\$", "")),
            region,
            stackS3Bucket,
            stackS3Prefix,
            label,
            dockerImageTags.map {
              _.split(",").map { p =>
                val l = p.split(":")
                (l(0), l(1))
              }.toMap
            },
            printOnly
          )
          stack = this
          subcommandResult <- subcommands(
            createStackSubcommand(stack)(context),
            updateStackSubcommand(stack)(context),
            deleteStackSubcommand(stack)(context)
          )
        } yield subcommandResult
      }

  //  def stack(implicit context: ScaseAwsContext) =
  //    for {
  //      stack <- if (context.stack == "cloudtemp")
  //        (for {
  //          level <- context.level
  //        } yield
  //          Success(CloudtempMainStack(level))
  //          ).getOrElse(Failure(LevelMustBeSpecified))
  //      else if (context.stack == "cloudtemp-admin")
  //        Success(CloudtempAdminStack())
  //      else
  //        Failure(UnknownStack())
  //    } yield stack
  //
  //  def templateForStack(implicit context: ScaseAwsContext) =
  //    for {
  //      stack <- stack
  //      template <- stack.createStackTemplate
  //    } yield {
  //      println("generating template")
  //      template.toJson.prettyPrint
  //    }

  def uploadTemplateToS3(stackName: String, template: String)(implicit context: StackContext) = {
    println(s"uploading template for $stackName to s3")
    val templateBucket = context.s3Bucket.getOrElse(???)
    val templateFileName = s"$stackName-stack.json"
    val templatePath = s"cloudtemp-aws/$templateFileName"
    s3PutText(templateBucket, s"$templatePath", template)
    httpsUrl(templateBucket, templatePath)
  }

  def nameStack[T](implicit context: StackContext) = {
    val stackName = s"${context.stackName}${context.label.map("-" + _).getOrElse("")}"

    (stackName, context.label)
  }

  def nameAndUploadTemplate[T](template: Template)(f: (String, String) => Try[T])(implicit context: StackContext) = {
    val templateSource = template.toJson.prettyPrint
    val (stackName, _) = nameStack

    println(template)
    f(stackName, uploadTemplateToS3(stackName, templateSource))
  }

  def createStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
    subcommand("create-stack")
      .header("Create a new stack.")
      .description("This command creates a new stack based on the specified stack name, level and optional label.") {
        for {
          accountId <- getAccount
          s3Bucket = context.s3Bucket.getOrElse(s"scase-$accountId")
          _ <- s3CreateBucket(s3Bucket, context.defaultRegion) handleErrorWith { _ =>
            IO(logger.info(s"bucket $s3Bucket already exists"))
          }
          contextWithBucket = context.copy(s3Bucket = Some(s3Bucket))
          template <- stack.template(contextWithBucket)
          stack <-
            if (context.printOnly)
              Try(println(template.toJson.prettyPrint))
            else
              nameAndUploadTemplate(template) { (stackName, templateUrl) =>
                println(s"creating stack $stackName from $templateUrl")
                Try(createStack(stackName, templateUrl))
              }(contextWithBucket)
        } yield stack
      }

  case class NotUpdatingStack(stackName: String) extends RuntimeException(s"updates to stack $stackName will not be applied")

  def updateStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
    subcommand("update-stack")
      .header("Update an existing stack.")
      .description("This command updates an existing stack based on the specified stack name, level and optional label.") {
        for {
          template <- stack.template
          stack <-
            if (context.printOnly)
              Try(println(template))
            else
              nameAndUploadTemplate(template) { (stackName, templateUrl) =>

                println(s"generating changeset for stack $stackName from $templateUrl")

                for {
                  changeSet <- createChangeSetAndWaitForComplete(stackName, templateUrl)
                  result <- {
                    println(changeSet)
                    val changes = changeSet.getChanges.asScala
                    if (changes.isEmpty)
                      Success(println("nothing to change"))
                    else
                      Try {
                        println("\nThe following changes will be applied:\n")
                        for {
                          c <- changes
                        } println(s"${c.getResourceChange.getAction} ${c.getResourceChange.getLogicalResourceId} ${c.getResourceChange.getResourceType} ${c.getResourceChange.getReplacement}")
                        println

                        if (readLine("Proceed with the update? ").equalsIgnoreCase("y")) {
                          println(s"updating stack $stackName from $templateUrl")
                          Try(updateStack(stackName, templateUrl))
                        } else {
                          Failure(NotUpdatingStack(stackName))
                        }
                      }
                  }
                } yield result
              }
        } yield stack
      }

  def deleteStackWithCleanup[T](stackName: String, levelAndLabel: Option[String], cleanupResources: (String, Option[String]) => Try[_] = { (_, _) => Success(()) }): Try[DeleteStackResult] = for {
    _ <- cleanupResources(stackName, levelAndLabel)
  } yield {
    println(s"deleting stack $stackName")
    deleteStack(stackName)
  }

  case object CannotDeleteProdStack extends IllegalStateException(s"cannot delete prod stack")

  def deleteStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
    subcommand("delete-stack")
      .header("Delete an existing stack.")
      .description("This command deletes an existing stack based on the specified stack name, level and optional label.") {
        fromTry {
          val (stackName, levelAndLabel) = nameStack

          deleteStackWithCleanup(stackName, levelAndLabel)
        }
      }

  case object LevelMustBeSpecified extends IllegalStateException(s"level must be specified for this stack")

}
