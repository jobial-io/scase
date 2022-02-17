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
package io.jobial.scase.aws.client

import cats.effect.{IO, Timer}
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.cloudformation.{AmazonCloudFormation, AmazonCloudFormationClientBuilder}
import cats.implicits._
import io.jobial.scase.util.Hash.uuid

import scala.concurrent.duration.DurationInt
import scala.util.{Success, Try}

trait CloudformationClient extends AwsClient {
  lazy val cloudformation = buildAwsClient[AmazonCloudFormationClientBuilder, AmazonCloudFormation](AmazonCloudFormationClientBuilder.standard)


  def createStack(stackName: String, templateUrl: Option[String], templateBody: Option[String]) = IO {
    val request = new CreateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)
    cloudformation.createStack(request)
  }

  def updateStack(stackName: String, templateUrl: Option[String], templateBody: Option[String]) = IO {
    val request = new UpdateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    cloudformation.updateStack(request)
  }

  def deleteStack(stackName: String) = IO {
    cloudformation.deleteStack(new DeleteStackRequest().withStackName(stackName))
  }

  def describeStackResources(stackName: String) = IO {
    cloudformation.describeStackResources(new DescribeStackResourcesRequest().withStackName(stackName)).getStackResources
  }

  def createChangeSet(stackName: String, templateUrl: Option[String], templateBody: Option[String], changeSetName: Option[String] = None) = IO {
    val request = new CreateChangeSetRequest()
      .withStackName(stackName)
      .withChangeSetName(changeSetName.getOrElse(s"$stackName-${uuid()}"))
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    cloudformation.createChangeSet(request)
  }

  def describeChangeSet(changeSetName: String) = IO {
    cloudformation.describeChangeSet(new DescribeChangeSetRequest().withChangeSetName(changeSetName))
  }

  def createChangeSetAndWaitForComplete(stackName: String, templateUrl: Option[String], templateBody: Option[String])(implicit timer: Timer[IO]) = {
    def waitForComplete(changeSetName: String): IO[DescribeChangeSetResult] =
      for {
        changeSet <- describeChangeSet(changeSetName)
        result <-
          if (changeSet.getStatus === "CREATE_COMPLETE" || changeSet.getStatus === "FAILED")
            IO(changeSet)
          else for {
            // Wait for change set to complete...
            _ <- IO.sleep(3.seconds)
            r <- waitForComplete(changeSetName)
          } yield r
      } yield result

    for {
      changeSet <- createChangeSet(stackName, templateUrl, templateBody)
      describeResult <- waitForComplete(changeSet.getId)
    } yield describeResult
  }
}
