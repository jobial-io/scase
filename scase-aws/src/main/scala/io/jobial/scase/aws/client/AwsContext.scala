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

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsAsyncClientBuilder
import com.amazonaws.client.builder.AwsSyncClientBuilder
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.endpointdiscovery.DaemonThreadFactory
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder
import com.amazonaws.services.ecr.AmazonECRAsyncClientBuilder
import com.amazonaws.services.ecs.AmazonECSAsyncClientBuilder
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder
import com.amazonaws.services.logs.AWSLogsAsyncClientBuilder
import com.amazonaws.services.rds.AmazonRDSAsyncClientBuilder
import com.amazonaws.services.route53.AmazonRoute53AsyncClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.secretsmanager.AWSSecretsManagerAsyncClientBuilder
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient

import java.util.concurrent.Executors

class AwsContext(
  val credentials: Option[AWSCredentials],
  val region: Option[String],
  val sqsExtendedS3BucketName: Option[String]
) {

  /**
   * Clients are supposed to be thread safe: https://forums.aws.amazon.com/message.jspa?messageID=191621
   */
  def buildAwsClient[BuilderClass <: AwsSyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsSyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = region match {
      case Some(region) =>
        awsClientBuilder.withRegion(region)
      case None =>
        awsClientBuilder
    }

    val b2 = credentials match {
      case Some(credentials) =>
        b1.withCredentials(new AWSStaticCredentialsProvider(credentials))
      case None =>
        b1
    }

    val b3 = b2.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))

    b3.build
  }

  def buildAwsAsyncClient[BuilderClass <: AwsAsyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsAsyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = region match {
      case Some(region) =>
        awsClientBuilder.withRegion(region)
      case None =>
        awsClientBuilder
    }

    val b2 = credentials match {
      case Some(credentials) =>
        b1.withCredentials(new AWSStaticCredentialsProvider(credentials))
      case _ =>
        b1
    }

    val b3 = b2.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))
      .withExecutorFactory(new ExecutorFactory {
        def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
      })

    b3.build
  }

  lazy val s3 = buildAwsClient[AmazonS3ClientBuilder, AmazonS3](AmazonS3ClientBuilder.standard)

  lazy val ec2 = AmazonEC2AsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val ecs = AmazonECSAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val sqs = new AmazonSQSBufferedAsyncClient(buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard))
  //lazy val sqs = buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard)

  lazy val sqsExtended = sqsExtendedS3BucketName.map { sqsExtendedS3BucketName =>
    new AmazonSQSExtendedClient(sqs, new ExtendedClientConfiguration()
      .withLargePayloadSupportEnabled(s3, sqsExtendedS3BucketName))
  }

  lazy val lambda = buildAwsAsyncClient[AWSLambdaAsyncClientBuilder, AWSLambdaAsync](AWSLambdaAsyncClientBuilder.standard)

  lazy val sts = buildAwsClient[AWSSecurityTokenServiceClientBuilder, AWSSecurityTokenService](AWSSecurityTokenServiceClientBuilder.standard)

  lazy val cloudformation = buildAwsClient[AmazonCloudFormationClientBuilder, AmazonCloudFormation](AmazonCloudFormationClientBuilder.standard)

  lazy val route53 = AmazonRoute53AsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val secretsManager = AWSSecretsManagerAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val cloudwatch = AmazonCloudWatchAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val logs = AWSLogsAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val ecr = AmazonECRAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  lazy val rds = AmazonRDSAsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build
}

object AwsContext {

  def apply(
    credentials: Option[AWSCredentials] = None,
    region: Option[String] = sys.env.get("AWS_DEFAULT_REGION"),
    sqsExtendedS3BucketName: Option[String] = None
  ) = new AwsContext(credentials, region, sqsExtendedS3BucketName)
}