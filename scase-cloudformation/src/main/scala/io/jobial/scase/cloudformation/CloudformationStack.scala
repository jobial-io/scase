package io.jobial.scase.cloudformation

import com.monsanto.arch.cloudformation.model.Template
import io.jobial.scase.aws.util.S3Client
import spray.json.DefaultJsonProtocol

import scala.util.Try


trait CloudformationStack
  extends CloudformationSupport
    with S3Client
    with DefaultJsonProtocol {

  def createStackTemplate(implicit context: ScaseAwsContext): Template

  def cleanupStack(stackName: String, levelAndLabel: Option[String]): Try[_]
}

