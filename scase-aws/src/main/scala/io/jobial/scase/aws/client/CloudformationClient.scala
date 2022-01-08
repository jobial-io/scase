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

import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.cloudformation.{AmazonCloudFormation, AmazonCloudFormationClientBuilder}
import io.jobial.scase.aws.client.Hash.uuid
import cats.implicits._
import scala.util.{Success, Try}

trait CloudformationClient extends AwsClient {
  lazy val cloudformation = buildAwsClient[AmazonCloudFormationClientBuilder, AmazonCloudFormation](AmazonCloudFormationClientBuilder.standard)


  def createStack(stackName: String, templateUrl: String): CreateStackResult = {
    val request = new CreateStackRequest().withStackName(stackName).withTemplateURL(templateUrl)
      .withCapabilities("CAPABILITY_NAMED_IAM")
    cloudformation.createStack(request)
  }

  def updateStack(stackName: String, templateUrl: String) = {
    val request = new UpdateStackRequest().withStackName(stackName).withTemplateURL(templateUrl)
      .withCapabilities("CAPABILITY_NAMED_IAM")
    cloudformation.updateStack(request)
  }

  def deleteStack(stackName: String) =
    cloudformation.deleteStack(new DeleteStackRequest().withStackName(stackName))

  def describeStackResources(stackName: String) =
    cloudformation.describeStackResources(new DescribeStackResourcesRequest().withStackName(stackName)).getStackResources

  def createChangeSet(stackName: String, templateUrl: String, changeSetName: Option[String] = None) = {
    val request = new CreateChangeSetRequest()
      .withStackName(stackName)
      .withChangeSetName(changeSetName.getOrElse(s"$stackName-${uuid()}"))
      .withTemplateURL(templateUrl)
      .withCapabilities("CAPABILITY_NAMED_IAM")
    cloudformation.createChangeSet(request)
  }

  def describeChangeSet(changeSetName: String) =
    cloudformation.describeChangeSet(new DescribeChangeSetRequest().withChangeSetName(changeSetName))

  def createChangeSetAndWaitForComplete(stackName: String, templateUrl: String) = {
    def waitForComplete(changeSetName: String): Try[DescribeChangeSetResult] =
      for {
        changeSet <- Try(describeChangeSet(changeSetName))
        result <-
          if (changeSet.getStatus === "CREATE_COMPLETE" || changeSet.getStatus === "FAILED")
            Success(changeSet)
          else {
            // Wait for change set to complete...
            Thread.sleep(3000)
            waitForComplete(changeSetName)
          }
      } yield result

    for {
      changeSet <- Try(createChangeSet(stackName, templateUrl))
      describeResult <- waitForComplete(changeSet.getId)
    } yield describeResult
  }
}
