package io.jobial.scase.aws.util

import java.util.concurrent.ExecutionException

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.{AwsAsyncClientBuilder, AwsSyncClientBuilder}

import scala.concurrent.Future.failed
import scala.concurrent.{ExecutionContext, Future}

trait AwsClient {

  def awsContext = AwsContext()
  
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

    b3.build
  }

  implicit def toScalaFuture[T](f: java.util.concurrent.Future[T])(implicit ec: ExecutionContext) = Future {
    // unfortunately we cannot do any better here...
    f.get
  } recoverWith {
    case t: ExecutionException =>
      failed(t.getCause)
    case t =>
      failed(t)
  }

}
