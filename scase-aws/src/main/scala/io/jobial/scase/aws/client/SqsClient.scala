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

import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Sync
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest
import io.jobial.sprint.logging.Logging
import io.jobial.sprint.util.CatsUtils
import io.jobial.sprint.util.IOShutdownHook
import java.util
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

trait SqsClient[F[_]] extends S3Client[F] {
  
  val defaultMaxReceiveMessageWaitTime = 20

  def createQueue(queueName: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <-
        trace(s"creating SQS queue $queueName") >>
          delay {
            val request = new CreateQueueRequest(queueName)
              .addAttributesEntry("ReceiveMessageWaitTimeSeconds", defaultMaxReceiveMessageWaitTime.toString)
              .addAttributesEntry("MessageRetentionPeriod", "86400")

            context.sqs.createQueue(request)
          }.handleErrorWith { t =>
            enableLongPolling(context.sqs.getQueueUrl(queueName).getQueueUrl) >>
              raiseError(t)
          }
      _ <- enableLongPolling(r.getQueueUrl)
    } yield r

  def initializeQueue(queueUrl: String, messageRetentionPeriod: Option[Duration],
    visibilityTimeout: Option[Duration], cleanup: Boolean)(implicit context: AwsContext, concurrent: Concurrent[F]) = {

    def setupQueue =
      messageRetentionPeriod.map(setMessageRetentionPeriod(queueUrl, _)).getOrElse(unit) >>
        visibilityTimeout.map(setVisibilityTimeout(queueUrl, _)).getOrElse(unit)

    {
      createQueue(queueUrl) >> whenA(cleanup)(new IOShutdownHook {
        def run =
          trace(s"deleting queue $queueUrl") >>
            pure(context.sqs.deleteQueue(queueUrl)).handleErrorWith { t =>
              raiseError[Unit](new IllegalStateException(s"error deleting queue $queueUrl", t))
            } >> trace(s"deleted queue $queueUrl")
      }.add) >>
        trace(s"created SQS queue $queueUrl")
    }.handleErrorWith { t =>
      setupQueue >> trace(s"could not create SQS queue $queueUrl")
    } >> setupQueue >>
      trace(s"initialized SQS queue $queueUrl")
  }


  def sendMessage(queueUrl: String, message: String, attributes: Map[String, String] = Map())(implicit context: AwsContext, concurrent: Concurrent[F]) = {
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
          delay(context.sqsExtended.getOrElse(context.sqs).sendMessage(request))
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
  def enableLongPolling(queueUrl: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString, defaultMaxReceiveMessageWaitTime.toString))
  }

  def setMessageRetentionPeriod(queueUrl: String, messageRetentionPeriod: Duration)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.MessageRetentionPeriod
        .toString, messageRetentionPeriod.toSeconds.toString))
  }

  def setVisibilityTimeout(queueUrl: String, visibilityTimeout: Duration)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    // TODO: report this bug
    assert(!context.sqs.isInstanceOf[AmazonSQSBufferedAsyncClient] || visibilityTimeout > (0.seconds),
      "Cannot set visibility timeout to 0 seconds on a buffered client due to a bug which casues hanging in receiveMessages")

    context.sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.VisibilityTimeout
        .toString, visibilityTimeout.toSeconds.toString))
  }

  // TODO: using async client
  def receiveMessage(queueUrl: String, maxNumberOfMessages: Int = 10, maxReceiveMessageWaitTime: Int = defaultMaxReceiveMessageWaitTime)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    delay {
      context.sqsExtended.getOrElse(context.sqs).receiveMessage(new ReceiveMessageRequest()
        .withQueueUrl(queueUrl)
        .withAttributeNames("All")
        .withMessageAttributeNames("All")
        .withMaxNumberOfMessages(maxNumberOfMessages)
        .withWaitTimeSeconds(maxReceiveMessageWaitTime)
      )
    }

  def deleteMessage(queueUrl: String, receiptHandle: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    context.sqsExtended.getOrElse(context.sqs).deleteMessage(queueUrl, receiptHandle)

  def changeMessageVisibility(queueUrl: String, receiptHandle: String, visibilityTimeout: Int)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    context.sqsExtended.getOrElse(context.sqs).changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout)

  def deleteQueue(queueUrl: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.sqs.deleteQueue(queueUrl)
  } handleErrorWith {
    case t =>
      error(s"deleting queue failed for $queueUrl: ", t) >>
        raiseError(t)
  }

}
