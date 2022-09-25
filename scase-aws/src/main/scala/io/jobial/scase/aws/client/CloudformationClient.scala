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

import cats.implicits._
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import io.jobial.scase.util.Hash.uuid

trait CloudformationClient[F[_]] extends AwsClient[F] {
  lazy val cloudformation = buildAwsClient[AmazonCloudFormationClientBuilder, AmazonCloudFormation](AmazonCloudFormationClientBuilder.standard)

  def createStack(stackName: String, templateUrl: Option[String], templateBody: Option[String]) = delay {
    val request = new CreateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)
    cloudformation.createStack(request)
  }

  def updateStack(stackName: String, templateUrl: Option[String], templateBody: Option[String]) = delay {
    val request = new UpdateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    cloudformation.updateStack(request)
  }

  def deleteStack(stackName: String) = delay {
    cloudformation.deleteStack(new DeleteStackRequest().withStackName(stackName))
  }

  def describeStackResources(stackName: String) = delay {
    cloudformation.describeStackResources(new DescribeStackResourcesRequest().withStackName(stackName)).getStackResources
  }

  def createChangeSet(stackName: String, templateUrl: Option[String], templateBody: Option[String], changeSetName: Option[String] = None) = delay {
    val request = new CreateChangeSetRequest()
      .withStackName(stackName)
      .withChangeSetName(changeSetName.getOrElse(s"$stackName-${uuid()}"))
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    cloudformation.createChangeSet(request)
  }

  def describeChangeSet(changeSetName: String) = delay {
    cloudformation.describeChangeSet(new DescribeChangeSetRequest().withChangeSetName(changeSetName))
  }

  def createChangeSetAndWaitForComplete(stackName: String, templateUrl: Option[String], templateBody: Option[String]) = {
    for {
      changeSet <- createChangeSet(stackName, templateUrl, templateBody)
      describeResult <- waitFor(describeChangeSet(changeSet.getId)) { changeSet =>
        pure(changeSet.getStatus === "CREATE_COMPLETE" || changeSet.getStatus === "FAILED")
      }
    } yield describeResult
  }
}
