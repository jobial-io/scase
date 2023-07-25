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
import io.jobial.sprint.util.CatsUtils

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
  with CatsUtils[F] with Logging {

  import awsContext.sqsClient

  def initialize =
    liftIO(sqsClient.initializeQueue(queueUrl, messageRetentionPeriod, visibilityTimeout, cleanup))

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]) = {
    for {
      _ <- trace(s"sending to queue $queueUrl ${message.toString.take(200)}")
      r <- sqsClient.sendMessage(queueUrl, Marshaller[M].marshalToText(message), attributes).to[F]
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