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
import cats.effect.IO
import cats.effect.Timer
import cats.implicits._
import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.model._
import java.util
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

trait SqsClient[F[_]] extends S3Client[F] {

  // TODO: find way to share sqs client and/or inject config; by default, each client creates hundreds of threads
  //  - see https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/sqs/AmazonSQSAsyncClient.html 
  lazy val sqs = new AmazonSQSBufferedAsyncClient(buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard))
  //lazy val sqs = buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard)

  lazy val sqsExtended = awsContext.sqsExtendedS3BucketName.map { sqsExtendedS3BucketName =>
    new AmazonSQSExtendedClient(sqs, new ExtendedClientConfiguration()
      .withLargePayloadSupportEnabled(s3, sqsExtendedS3BucketName))
  }

  val defaultMaxReceiveMessageWaitTime = 20

  def createQueue(queueName: String) =
    for {
      r <-
        info(s"creating SQS queue $queueName") >>
          delay {
            val request = new CreateQueueRequest(queueName)
              .addAttributesEntry("ReceiveMessageWaitTimeSeconds", defaultMaxReceiveMessageWaitTime.toString)
              .addAttributesEntry("MessageRetentionPeriod", "86400")

            sqs.createQueue(request)
          }.handleErrorWith { t =>
            enableLongPolling(sqs.getQueueUrl(queueName).getQueueUrl) >>
              raiseError(t)
          }
      _ <- enableLongPolling(r.getQueueUrl)
    } yield r

  def initializeQueue(queueUrl: String, messageRetentionPeriod: Option[Duration],
    visibilityTimeout: Option[Duration], cleanup: Boolean) = {

    def setupQueue =
      messageRetentionPeriod.map(setMessageRetentionPeriod(queueUrl, _)).getOrElse(unit) >>
        visibilityTimeout.map(setVisibilityTimeout(queueUrl, _)).getOrElse(unit)

    {
      createQueue(queueUrl) >>
        whenA(cleanup)(delay(sys.addShutdownHook { () => {
          trace[IO](s"deleting queue $queueUrl") >>
            IO(sqs.deleteQueue(queueUrl)).handleErrorWith { t =>
              raiseError[IO, Unit](new IllegalStateException(s"error deleting queue $queueUrl", t))
            }
        }.unsafeRunSync()

        })) >>
        trace(s"created SQS queue $queueUrl")
    }.handleErrorWith { t =>
      setupQueue >> trace(s"could not create SQS queue $queueUrl")
    } >> setupQueue >>
      trace(s"initialized SQS queue $queueUrl")
  }

  def sendMessage(queueUrl: String, message: String, attributes: Map[String, String] = Map())(implicit awsContext: AwsContext = AwsContext()) = {
    {
      for {
        request <- delay {
          new SendMessageRequest()
            .withQueueUrl(queueUrl)
            .withMessageBody(message)
            // we need to explicitly convert to a mutable java map here because the extended client needs to add further attributes...
            .withMessageAttributes(new util.Hashtable(attributes.mapValues {
              value =>
                new MessageAttributeValue().withDataType("String").withStringValue(value)
            }.toMap.asJava)
            )
        }
        r <- trace(s"message attributes: ${
          request.getMessageAttributes.asScala
        }") >>
          trace(s"calling sendMessage on queue $queueUrl with ${
            request.toString.take(200)
          }") >>
          delay(sqsExtended.getOrElse(sqs).sendMessage(request))
      } yield r
    } handleErrorWith {
      case t =>
        error(s"sendMessage failed on queue $queueUrl: ", t) >>
          raiseError(t)
    }
  }

  //  def sendMessage(queueUrl: String, message: JsValue): Try[SendMessageResult] =
  //    sendMessage(queueUrl, message.prettyPrint)
  //
  def enableLongPolling(queueUrl: String) = delay {
    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString, defaultMaxReceiveMessageWaitTime.toString))
  }

  def setMessageRetentionPeriod(queueUrl: String, messageRetentionPeriod: Duration) = delay {
    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.MessageRetentionPeriod
        .toString, messageRetentionPeriod.toSeconds.toString))
  }

  def setVisibilityTimeout(queueUrl: String, visibilityTimeout: Duration) = delay {
    // TODO: report this bug
    assert(!sqs.isInstanceOf[AmazonSQSBufferedAsyncClient] || visibilityTimeout > (0.seconds),
      "Cannot set visibility timeout to 0 seconds on a buffered client due to a bug which casues hanging in receiveMessages")

    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.VisibilityTimeout
        .toString, visibilityTimeout.toSeconds.toString))
  }

  // TODO: using async client
  def receiveMessage(queueUrl: String, maxNumberOfMessages: Int = 10, maxReceiveMessageWaitTime: Int = defaultMaxReceiveMessageWaitTime) =
    delay {
      sqsExtended.getOrElse(sqs).receiveMessage(new ReceiveMessageRequest()
        .withQueueUrl(queueUrl)
        .withAttributeNames("All")
        .withMessageAttributeNames("All")
        .withMaxNumberOfMessages(maxNumberOfMessages)
        .withWaitTimeSeconds(maxReceiveMessageWaitTime)
      )
    }

  def deleteMessage(queueUrl: String, receiptHandle: String) =
    sqsExtended.getOrElse(sqs).deleteMessage(queueUrl, receiptHandle)

  def changeMessageVisibility(queueUrl: String, receiptHandle: String, visibilityTimeout: Int) =
    sqsExtended.getOrElse(sqs).changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout)

  def deleteQueue(queueUrl: String) = delay {
    sqs.deleteQueue(queueUrl)
  } handleErrorWith {
    case t =>
      error(s"deleting queue failed for $queueUrl: ", t) >>
        raiseError(t)
  }

}

object SqsClient {

  def apply[F[_] : Timer : Concurrent](implicit context: AwsContext) = {
    new SqsClient[F] {
      val awsContext = context

      val concurrent = Concurrent[F]

      val timer = Timer[F]
    }
  }
}