package io.jobial.scase.aws.sqs

import cats.{Monad, Traverse}
import cats.effect.{Concurrent, IO}
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.aws.client.{AwsContext, SqsClient}
import io.jobial.scase.core._
import io.jobial.scase.core.impl.DefaultMessageSendResult
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Producer implementation for AWS SQS.
 */
class SqsProducer[F[_] : Concurrent, M](
  queueUrl: String,
  messageRetentionPeriod: Option[Duration] = Some(1.hour),
  visibilityTimeout: Option[Duration] = Some(10.minutes),
  cleanup: Boolean = false
)(
  implicit val awsContext: AwsContext
) extends MessageProducer[F, M]
  with Logging {

  import awsContext.sqsClient._

  def initialize(implicit concurrent: Concurrent[F]) =
    concurrent.liftIO(for {
      _ <- createQueueIfNotExists(queueUrl)
      _ <- if (cleanup) IO(sys.addShutdownHook({ () =>
        try {
          logger.debug(s"deleting queue $queueUrl")
          deleteQueue(queueUrl).unsafeRunSync()
        } catch {
          case t: Throwable =>
            throw new RuntimeException(s"error deleting queue $queueUrl", t)
        }
      })) else IO()
      _ <- debug[IO](s"created queue $queueUrl")
      _ <- messageRetentionPeriod.map(setMessageRetentionPeriod(queueUrl, _)).getOrElse(IO())
      _ <- visibilityTimeout.map(setVisibilityTimeout(queueUrl, _)).getOrElse(IO())
    } yield ())

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]) = {
    logger.info(s"sending to queue $queueUrl ${message.toString.take(200)}")
    val r: F[MessageSendResult[F, M]] = for {
      r <- sendMessage(queueUrl, Marshaller[M].marshalToText(message), attributes).to[F]
    } yield {
      logger.info(s"successfully sent to queue $queueUrl ${message.toString.take(200)}")
      DefaultMessageSendResult[F, M](unit, unit)
    }

    r handleErrorWith { t =>
      logger.error(s"failed sending to queue $queueUrl $message", t)
      logger.error(s"failure sending to queue $queueUrl ${message.toString.take(200)}", t)
      r
    }
  }

  def stop = unit
}

object SqsProducer {

  def apply[F[_] : Concurrent, M](
    queueUrl: String,
    messageRetentionPeriod: Option[Duration] = Some(1.hour),
    visibilityTimeout: Option[Duration] = Some(10.minutes),
    cleanup: Boolean = false
  )(
    implicit awsContext: AwsContext
  ) = {
    val producer = new SqsProducer[F, M](queueUrl, messageRetentionPeriod, visibilityTimeout, cleanup)
    for {
      _ <- producer.initialize
    } yield producer
  }


}