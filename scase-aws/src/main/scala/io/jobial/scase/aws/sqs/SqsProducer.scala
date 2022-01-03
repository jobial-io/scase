package io.jobial.scase.aws.sqs

import cats.Traverse
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.aws.client.identitymap.identityTrieMap
import io.jobial.scase.aws.client.{AwsContext, SqsClient}
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Producer implementation for AWS SQS.
 */
case class SqsProducer[F[_], M](
  queueUrl: String,
  messageRetentionPeriod: Option[Duration] = Some(1.hour),
  visibilityTimeout: Option[Duration] = Some(10.minutes),
  cleanup: Boolean = false
)(
  implicit val awsContext: AwsContext
) extends MessageProducer[F, M]
  with Logging {

  import awsContext.sqsClient._

  createQueueIfNotExists(queueUrl)
  
  if (cleanup)
    sys.addShutdownHook {
      try {
        println(s"deleting queue $queueUrl")
        deleteQueue(queueUrl)
      } catch {
        case t: Throwable =>
          throw new RuntimeException(s"error deleting queue $queueUrl", t)
      }
    }

  logger.debug(s"created queue $queueUrl")

  messageRetentionPeriod.map(setMessageRetentionPeriod(queueUrl, _))
  visibilityTimeout.map(setVisibilityTimeout(queueUrl, _))


  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], c: Concurrent[F]) = {
    logger.debug(s"sending to queue $queueUrl ${message.toString.take(200)}")
    val r: F[MessageSendResult[M]] = for {
      r <- sendMessage(queueUrl, Marshaller[M].marshalToText(message), attributes).to[F]
    } yield {
      logger.debug(s"successfully sent to queue $queueUrl ${message.toString.take(200)}")
      MessageSendResult[M]()
    }

    r handleErrorWith { t =>
      logger.error(s"failed sending to queue $queueUrl $message", t)
      logger.error(s"failure sending to queue $queueUrl ${message.toString.take(200)}", t)
      r
    }
  }


}

