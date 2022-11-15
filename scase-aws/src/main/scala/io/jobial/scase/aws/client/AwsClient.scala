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

import cats.effect.unsafe.IORuntime
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsAsyncClientBuilder
import com.amazonaws.client.builder.AwsSyncClientBuilder
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.endpointdiscovery.DaemonThreadFactory
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.TemporalEffect
import io.jobial.scase.logging.Logging
import java.util.concurrent.Executors

trait AwsClient[F[_]] extends CatsUtils with Logging {

  def awsContext: AwsContext

  protected implicit def temporal: TemporalEffect[F]

  protected implicit def runtime: IORuntime


  /**
   * Clients are supposed to be thread safe: https://forums.aws.amazon.com/message.jspa?messageID=191621
   */
  def buildAwsClient[BuilderClass <: AwsSyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsSyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = awsContext.region match {
      case Some(region) =>
        awsClientBuilder.withRegion(region)
      case None =>
        awsClientBuilder
    }

    val b2 = awsContext.credentials match {
      case Some(credentials) =>
        b1.withCredentials(new AWSStaticCredentialsProvider(credentials))
      case None =>
        b1
    }

    val b3 = b2.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))

    b3.build
  }

  def buildAwsAsyncClient[BuilderClass <: AwsAsyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsAsyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = awsContext.region match {
      case Some(region) =>
        awsClientBuilder.withRegion(region)
      case None =>
        awsClientBuilder
    }

    val b2 = awsContext.credentials match {
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

}
