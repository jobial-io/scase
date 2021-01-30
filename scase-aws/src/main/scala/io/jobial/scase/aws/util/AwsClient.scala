package io.jobial.scase.aws.util

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.{AwsAsyncClientBuilder, AwsSyncClientBuilder}

trait AwsClient {

  def awsContext: AwsContext

  /**
   * Clients are supposed to be thread safe: https://forums.aws.amazon.com/message.jspa?messageID=191621
   */
  def buildAwsClient[BuilderClass <: AwsSyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsSyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = awsClientBuilder.withRegion(awsContext.region)

    val b2 = awsContext.credentials match {
      case Some(credentials) =>
        b1.withCredentials(new AWSStaticCredentialsProvider(credentials))
      case _ =>
        b1
    }

    val b3 = b2.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))

    b3.build
  }

  def buildAwsAsyncClient[BuilderClass <: AwsAsyncClientBuilder[BuilderClass, BuiltClass], BuiltClass](awsClientBuilder: AwsAsyncClientBuilder[BuilderClass, BuiltClass]) = {
    val b1 = awsClientBuilder.withRegion(awsContext.region)

    val b2 = awsContext.credentials match {
      case Some(credentials) =>
        b1.withCredentials(new AWSStaticCredentialsProvider(credentials))
      case _ =>
        b1
    }

    val b3 = b2.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))

    b3.build
  }
  
}
