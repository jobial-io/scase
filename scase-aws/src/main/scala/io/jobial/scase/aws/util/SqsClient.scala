package io.jobial.scase.aws.util

import java.util

import com.amazon.sqs.javamessaging.{AmazonSQSExtendedClient, ExtendedClientConfiguration}
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import io.jobial.scase.logging.Logging

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Try}
import scala.collection.JavaConversions._

trait SqsClient extends AwsClient with Logging {
  this: S3Client =>

  // TODO: find way to share sqs client and/or inject config; by default, each client creates hundreds of threads
  //  - see https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/sqs/AmazonSQSAsyncClient.html 
  lazy val sqs = new AmazonSQSBufferedAsyncClient(buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard))
  //lazy val sqs = buildAwsAsyncClient[AmazonSQSAsyncClientBuilder, AmazonSQSAsync](AmazonSQSAsyncClientBuilder.standard)

  lazy val sqsExtended = awsContext.sqsExtendedS3BucketName.map { sqsExtendedS3BucketName =>
    new AmazonSQSExtendedClient(sqs, new ExtendedClientConfiguration()
      .withLargePayloadSupportEnabled(s3, sqsExtendedS3BucketName))
  }

  val defaultMaxReceiveMessageWaitTime = 20

  def createQueue(queueName: String): Try[CreateQueueResult] = {
    logger.info(s"creating SQS queue $queueName")
    val request = new CreateQueueRequest(queueName)
      .addAttributesEntry("ReceiveMessageWaitTimeSeconds", defaultMaxReceiveMessageWaitTime.toString)
      .addAttributesEntry("MessageRetentionPeriod", "86400")

    Try(sqs.createQueue(request))
  }

  def createQueueIfNotExists(queueName: String): Try[String] =
    createQueue(queueName).map(_.getQueueUrl) recoverWith {
      case e: AmazonSQSException =>
        if (e.getErrorCode().equals("QueueAlreadyExists"))
          Try(sqs.getQueueUrl(queueName).getQueueUrl).map { queueUrl =>
            enableLongPolling(queueUrl)
            queueUrl
          }
        else Failure(e)
      case t =>
        Failure(t)
    }

  def sendMessage(queueUrl: String, message: String, attributes: Map[String, String] = Map()): Try[SendMessageResult] =
    Try {
      try {
        val request = new SendMessageRequest()
          .withQueueUrl(queueUrl)
          .withMessageBody(message)
          // we need to explicitly convert to a mutable java map here because the extended client needs to add further attributes...
          .withMessageAttributes(new util.Hashtable(attributes.mapValues { value =>
            new MessageAttributeValue().withDataType("String").withStringValue(value)
          })
          )
        logger.debug(s"message attributes: ${request.getMessageAttributes.toMap}")
        logger.debug(s"calling sendMessage on queue $queueUrl with ${request.toString.take(200)}")
        sqsExtended.getOrElse(sqs).sendMessage(request)
      } catch {
        case t =>
          t.printStackTrace
          throw t
      }
    } recoverWith {
      case t =>
        logger.error(s"sendMessage failed on queue $queueUrl: ", t)
        Failure(t)
    }

  //  def sendMessage(queueUrl: String, message: JsValue): Try[SendMessageResult] =
  //    sendMessage(queueUrl, message.prettyPrint)
  //
  def enableLongPolling(queueUrl: String) =
    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString, defaultMaxReceiveMessageWaitTime.toString))

  def setMessageRetentionPeriod(queueUrl: String, messageRetentionPeriod: Duration) = {
    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.MessageRetentionPeriod
        .toString, messageRetentionPeriod.toSeconds.toString))
  }

  def setVisibilityTimeout(queueUrl: String, visibilityTimeout: Duration) = {
    // TODO: report this bug
    assert(!sqs.isInstanceOf[AmazonSQSBufferedAsyncClient] || visibilityTimeout > (0.seconds),
      "Cannot set visibility timeout to 0 seconds on a buffered client due to a bug which casues hanging in receiveMessages")

    sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueUrl)
      .addAttributesEntry(QueueAttributeName.VisibilityTimeout
        .toString, visibilityTimeout.toSeconds.toString))
  }

  def receiveMessage(queueUrl: String, maxNumberOfMessages: Int = 10, maxReceiveMessageWaitTime: Int = defaultMaxReceiveMessageWaitTime) =
    sqsExtended.getOrElse(sqs).receiveMessage(new ReceiveMessageRequest()
      .withQueueUrl(queueUrl)
      .withAttributeNames("All")
      .withMessageAttributeNames("All")
      .withMaxNumberOfMessages(maxNumberOfMessages)
      .withWaitTimeSeconds(maxReceiveMessageWaitTime)
    )

  def deleteMessage(queueUrl: String, receiptHandle: String) =
    sqsExtended.getOrElse(sqs).deleteMessage(queueUrl, receiptHandle)

  def changeMessageVisibility(queueUrl: String, receiptHandle: String, visibilityTimeout: Int) =
    sqsExtended.getOrElse(sqs).changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout)

  def deleteQueue(queueUrl: String) = Try {
    sqs.deleteQueue(queueUrl)
  } recoverWith {
    case t =>
      logger.error(s"deleting queue failed for $queueUrl: ", t)
      Failure(t)
  }

}
