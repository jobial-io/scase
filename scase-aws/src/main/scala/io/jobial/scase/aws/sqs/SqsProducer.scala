package io.jobial.scase.aws.sqs

import cats.effect.LiftIO
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core._
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.core.impl.DefaultMessageSendResult
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import scala.concurrent.duration._

/**
 * Producer implementation for AWS SQS.
 */
class SqsProducer[F[_] : ConcurrentEffect : LiftIO, M](
  queueUrl: String,
  messageRetentionPeriod: Option[Duration] = Some(1.hour),
  visibilityTimeout: Option[Duration] = Some(10.minutes),
  cleanup: Boolean = false
)(
  implicit val awsContext: AwsContext
) extends MessageProducer[F, M]
  with Logging {

  import awsContext.sqsClient._

  def initialize =
    liftIO(initializeQueue(queueUrl, messageRetentionPeriod, visibilityTimeout, cleanup))

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]) = {
    for {
      _ <- trace(s"sending to queue $queueUrl ${message.toString.take(200)}")
      r <- sendMessage(queueUrl, Marshaller[M].marshalToText(message), attributes).to[F]
      _ <- trace(s"successfully sent to queue $queueUrl ${message.toString.take(200)}")
    } yield
      DefaultMessageSendResult[F, M](unit, unit): MessageSendResult[F, M]
  }.handleErrorWith { t =>
    error(s"failed sending to queue $queueUrl ${message.toString.take(200)}", t) >>
      raiseError(t)
  }

  def stop = unit
}

object SqsProducer {

  def apply[F[_] : ConcurrentEffect : LiftIO, M](
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