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

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.services.cloudformation.model._
import io.jobial.scase.util.Hash.uuid

trait CloudformationClient[F[_]] extends AwsClient[F] {
  def createStack(stackName: String, templateUrl: Option[String], templateBody: Option[String])(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new CreateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)
    context.cloudformation.createStack(request)
  }

  def updateStack(stackName: String, templateUrl: Option[String], templateBody: Option[String])(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new UpdateStackRequest().withStackName(stackName)
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    context.cloudformation.updateStack(request)
  }

  def deleteStack(stackName: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.cloudformation.deleteStack(new DeleteStackRequest().withStackName(stackName))
  }

  def describeStackResources(stackName: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.cloudformation.describeStackResources(new DescribeStackResourcesRequest().withStackName(stackName)).getStackResources
  }

  def createChangeSet(stackName: String, templateUrl: Option[String], templateBody: Option[String], changeSetName: Option[String] = None)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new CreateChangeSetRequest()
      .withStackName(stackName)
      .withChangeSetName(changeSetName.getOrElse(s"$stackName-${uuid()}"))
      .withCapabilities("CAPABILITY_NAMED_IAM")

    templateUrl.map(request.withTemplateURL)
    templateBody.map(request.withTemplateBody)

    context.cloudformation.createChangeSet(request)
  }

  def describeChangeSet(changeSetName: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.cloudformation.describeChangeSet(new DescribeChangeSetRequest().withChangeSetName(changeSetName))
  }

  def createChangeSetAndWaitForComplete(stackName: String, templateUrl: Option[String], templateBody: Option[String])(implicit context: AwsContext, concurrent: Concurrent[F], timer: Timer[F]) = {
    for {
      changeSet <- createChangeSet(stackName, templateUrl, templateBody)
      describeResult <- waitFor(describeChangeSet(changeSet.getId)) { changeSet =>
        pure(changeSet.getStatus === "CREATE_COMPLETE" || changeSet.getStatus === "FAILED")
      }
    } yield describeResult
  }
}
