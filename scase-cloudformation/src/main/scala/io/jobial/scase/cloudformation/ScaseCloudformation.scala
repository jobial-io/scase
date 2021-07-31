package io.jobial.scase.cloudformation

import com.amazonaws.services.cloudformation.model.DeleteStackResult
import com.monsanto.arch.cloudformation.model.Template
import io.jobial.scase.aws.client.{AwsContext, CloudformationClient, S3Client}
import io.jobial.sclap.CommandLineApp
import io.jobial.sclap.core.ArgumentValueParser
import spray.json._

import scala.util.{Failure, Success, Try}
import collection.JavaConverters._
import scala.io.StdIn.readLine

//object ScaseCloudformation 
//  extends CloudformationStackApp
//  with DefaultJsonProtocol
//  with S3Client
//  with CloudformationClient {
//
//  def run =
//    command
//      .header("Scase AWS Tool")
//      .description("Tool for managing Cloudtemp AWS resources and generating Cloudformation templates.") {
//        for {
//          stackName <- opt[String]("--stack")
//            .description("The name of the stack")
//            .required
//          label <- opt[String]("--label")
//            .description("An additional discriminator label for the stack where applicable")
//          dockerImageTags <- opt[String]("--docker-image-tags")
//            .description("Mapping of docker images to tags, in the format <image_name>:<tag>,... . " +
//              "By default, the stack will use the latest tag for images. This option allows to override this.")
//          printOnly <- opt[Boolean]("--print-only").default(false)
//            .description("Print the generated template and operations, do not make any changes")
//          stackClassName <- param[String].label("<stack_class_name>").required
//          context = StackContext(
//            stackName,
//            label,
//            dockerImageTags.map {
//              _.split(",").map { p =>
//                val l = p.split(":")
//                (l(0), l(1))
//              }.toMap
//            },
//            printOnly
//          )
//          stack = Class.forName(stackClassName).newInstance().asInstanceOf[CloudformationStack]
//          subcommandResult <- subcommands(
//            createStackSubcommand(stack)(context),
//            updateStackSubcommand(stack)(context),
//            deleteStackSubcommand(stack)(context)
//          )
//        } yield subcommandResult
//      }
//
//  //  def stack(implicit context: ScaseAwsContext) =
//  //    for {
//  //      stack <- if (context.stack == "cloudtemp")
//  //        (for {
//  //          level <- context.level
//  //        } yield
//  //          Success(CloudtempMainStack(level))
//  //          ).getOrElse(Failure(LevelMustBeSpecified))
//  //      else if (context.stack == "cloudtemp-admin")
//  //        Success(CloudtempAdminStack())
//  //      else
//  //        Failure(UnknownStack())
//  //    } yield stack
//  //
//  //  def templateForStack(implicit context: ScaseAwsContext) =
//  //    for {
//  //      stack <- stack
//  //      template <- stack.createStackTemplate
//  //    } yield {
//  //      println("generating template")
//  //      template.toJson.prettyPrint
//  //    }
//
//  def uploadTemplateToS3(stackName: String, template: String) = {
//    println(s"uploading template for $stackName to s3")
//    val templateBucket = "cloudtemp-admin"
//    val templateFileName = s"$stackName-stack.json"
//    val templatePath = s"cloudtemp-aws/$templateFileName"
//    s3PutText(templateBucket, s"$templatePath", template)
//    httpsUrl(templateBucket, templatePath)
//  }
//
//  def nameStack[T](implicit context: StackContext) = {
//    val stackName = s"${context.stackName}${context.label.map("-" + _).getOrElse("")}"
//
//    (stackName, context.label)
//  }
//
//  def nameAndUploadTemplate[T](template: Template)(f: (String, String) => Try[T])(implicit context: StackContext) = {
//    val templateSource = template.toJson.prettyPrint
//    val (stackName, _) = nameStack
//
//    println(template)
//    f(stackName, uploadTemplateToS3(stackName, templateSource))
//  }
//
//  def createStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
//    subcommand("create-stack")
//      .header("Create a new stack.")
//      .description("This command creates a new stack based on the specified stack name, level and optional label.") {
//        val template = stack.template
//        for {
//          t <- template
//          stack <-
//            if (context.printOnly)
//              Try(println(template))
//            else
//              nameAndUploadTemplate(t) { (stackName, templateUrl) =>
//                println(s"creating stack $stackName from $templateUrl")
//                Try(createStack(stackName, templateUrl))
//              }
//        } yield stack
//      }
//
//  case class NotUpdatingStack(stackName: String) extends RuntimeException(s"updates to stack $stackName will not be applied")
//
//  def updateStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
//    subcommand("update-stack")
//      .header("Update an existing stack.")
//      .description("This command updates an existing stack based on the specified stack name, level and optional label.") {
//        for {
//          template <- stack.template
//          stack <-
//            if (context.printOnly)
//              Try(println(template))
//            else
//              nameAndUploadTemplate(template) { (stackName, templateUrl) =>
//
//                println(s"generating changeset for stack $stackName from $templateUrl")
//
//                for {
//                  changeSet <- createChangeSetAndWaitForComplete(stackName, templateUrl)
//                  result <- {
//                    println(changeSet)
//                    val changes = changeSet.getChanges.asScala
//                    if (changes.isEmpty)
//                      Success(println("nothing to change"))
//                    else
//                      Try {
//                        println("\nThe following changes will be applied:\n")
//                        for {
//                          c <- changes
//                        } println(s"${c.getResourceChange.getAction} ${c.getResourceChange.getLogicalResourceId} ${c.getResourceChange.getResourceType} ${c.getResourceChange.getReplacement}")
//                        println
//
//                        if (readLine("Proceed with the update? ").equalsIgnoreCase("y")) {
//                          println(s"updating stack $stackName from $templateUrl")
//                          Try(updateStack(stackName, templateUrl))
//                        } else {
//                          Failure(NotUpdatingStack(stackName))
//                        }
//                      }
//                  }
//                } yield result
//              }
//        } yield stack
//      }
//
//  def deleteStackWithCleanup[T](stackName: String, levelAndLabel: Option[String], cleanupResources: (String, Option[String]) => Try[_] = { (_, _) => Success(()) }): Try[DeleteStackResult] = for {
//    _ <- cleanupResources(stackName, levelAndLabel)
//  } yield {
//    println(s"deleting stack $stackName")
//    deleteStack(stackName)
//  }
//
//  case object CannotDeleteProdStack extends IllegalStateException(s"cannot delete prod stack")
//
//  def deleteStackSubcommand(stack: CloudformationStack)(implicit context: StackContext) =
//    subcommand("delete-stack")
//      .header("Delete an existing stack.")
//      .description("This command deletes an existing stack based on the specified stack name, level and optional label.") {
//        fromTry {
//          val (stackName, levelAndLabel) = nameStack
//
//          deleteStackWithCleanup(stackName, levelAndLabel)
//        }
//      }
//
//  case object LevelMustBeSpecified extends IllegalStateException(s"level must be specified for this stack")
//
//}