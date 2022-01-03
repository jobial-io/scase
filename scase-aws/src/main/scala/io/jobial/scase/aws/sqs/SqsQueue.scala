package io.jobial.scase.aws.sqs

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, LiftIO}
import cats.implicits._
import cats.{Monad, Traverse}
import io.jobial.scase.aws.client.identitymap.identityTrieMap
import io.jobial.scase.aws.client.{AwsContext, S3Client, SqsClient}
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Queue implementation for AWS SQS.
 */
case class SqsQueue[F[_], M](
  queueUrl: String,
  name: String,
  messageRetentionPeriod: Option[Duration] = Some(1.hour),
  visibilityTimeout: Option[Duration] = Some(10.minutes),
  cleanup: Boolean = false
)(
  implicit val awsContext: AwsContext
) extends MessageQueue[F, M]
  with Logging {
  
  val producer = SqsProducer[F, M](queueUrl)
  val consumer = SqsConsumer[F, M](queueUrl)

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]] =
    producer.send(message, attributes)

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]] =
    consumer.subscribe(callback)
}

object SqsQueue {

  def create[F[_] : LiftIO : Monad, M](
    name: String,
    messageRetentionPeriod: Option[Duration] = Some(1.hour),
    visibilityTimeout: Option[Duration] = Some(10.minutes),
    cleanup: Boolean = false
  )(
    implicit awsContext: AwsContext
  ) =
    for {
      queueUrl <- awsContext.sqsClient.createQueueIfNotExists(name).to[F]
    } yield SqsQueue[F, M](
      queueUrl,
      name,
      messageRetentionPeriod,
      visibilityTimeout,
      cleanup
    )
}