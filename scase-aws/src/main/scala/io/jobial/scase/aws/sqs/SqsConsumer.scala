package io.jobial.scase.aws.sqs

import cats.{Monad, Traverse}
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Concurrent, IO, Sync}
import cats.implicits._
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageResult}
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.aws.client.IdentityMap.identityTrieMap
import io.jobial.scase.core._
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.math.min

/**
 * Consumer implementation for AWS SQS.
 */
class SqsConsumer[F[_] : Concurrent, M](
  queueUrl: String,
  outstandingMessagesRef: Ref[F, collection.Map[M, String]],
  receivedMessagesRef: Ref[F, List[Message]],
  receivedMessagesSemaphore: Semaphore[F],
  messageRetentionPeriod: Option[Duration],
  visibilityTimeout: Option[Duration],
  cleanup: Boolean
)(
  implicit val awsContext: AwsContext
) extends DefaultMessageConsumer[F, M]
  with CatsUtils
  with Logging {

  import awsContext.sqsClient._

  override def initialize =
    liftIO(initializeQueue(queueUrl, messageRetentionPeriod, visibilityTimeout, cleanup))

  def receiveMessagesFromQueue(timeout: Option[FiniteDuration]) =
    (for {
      _ <- receivedMessagesSemaphore.acquire
      receivedMessages <- receivedMessagesRef.get
      newMessages <-
        if (receivedMessages.isEmpty)
          for {
            newMessages <-
              trace(s"waiting for messages on $queueUrl") >>
                // TODO: handle timeout more precisely
                liftIO(receiveMessage(queueUrl, 10, timeout.map(t => min(1, t.toSeconds.toInt)).getOrElse(Int.MaxValue)).map(_.getMessages.asScala))
            _ <- trace(s"received messages ${newMessages.toString.take(500)} on queue $queueUrl")
          } yield newMessages
        else
          pure(List())
      message <- receivedMessagesRef.modify { r =>
        val allMessages = r ++ newMessages
        if (allMessages.isEmpty)
          (Nil, None)
        else
          (allMessages.tail, allMessages.headOption)
      }
      _ <- receivedMessagesSemaphore.release
    } yield message) handleErrorWith { t =>
      for {
        _ <- receivedMessagesSemaphore.release
        _ <- raiseError[F, Option[Message]](t)
      } yield None
    }

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      // TODO: set visibility timeout to 0 here to allow other clients receiving uncorrelated messages
      message <- receiveMessagesFromQueue(timeout)
      result <- {
        message match {
          case Some(sqsMessage) =>
            for {
              unmarshalledMessage <- Concurrent[F].fromEither(u.unmarshalFromText(sqsMessage.getBody))
              _ <- outstandingMessagesRef.update(_ + ((unmarshalledMessage, sqsMessage.getReceiptHandle)))
            } yield
              DefaultMessageReceiveResult[F, M](
                message = pure(unmarshalledMessage),
                // TODO: add standard attributes returned by getAttributes...
                attributes = sqsMessage.getMessageAttributes.asScala.toMap.filter(e => Option(e._2.getStringValue).isDefined).mapValues(_.getStringValue).toMap,
                Some(this),
                commit =
                  for {
                    o <- outstandingMessagesRef.get
                    r <- o.get(unmarshalledMessage) match {
                      case Some(receiptHandle) =>
                        trace(s"deleted message ${unmarshalledMessage.toString.take(500)}") >>
                          delay(deleteMessage(queueUrl, receiptHandle))
                      case _ =>
                        raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                    }
                    _ <- outstandingMessagesRef.update(_ - unmarshalledMessage)
                  } yield (),
                rollback = unit,
                underlyingMessageProvided = pure(sqsMessage.getBody),
                underlyingContextProvided = pure(sqsMessage)
                //                                outstandingMessages.remove(unmarshalledMessage) match {
                //                                  case Some(receiptHandle) =>
                //                                    // if the process fails at this point it will still roll back after the visibility timeout
                //                                    IO(changeMessageVisibility(queueUrl, receiptHandle, 0))
                //                                  case _ =>
                //                                    IO.raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                //                                }
              )
          case None =>
            raiseError(ReceiveTimeout(timeout))
        }
      }
    } yield result

  def stop = unit
}

object SqsConsumer {

  def apply[F[_] : Concurrent, M](
    queueUrl: String,
    messageRetentionPeriod: Option[Duration] = Some(1.hour),
    visibilityTimeout: Option[Duration] = Some(10.minutes),
    cleanup: Boolean = false
  )(
    implicit awsContext: AwsContext
  ): F[SqsConsumer[F, M]] = for {
    outstandingMessagesRef <- Ref.of[F, collection.Map[M, String]](identityTrieMap[M, String])
    receivedMessagesRef <- Ref.of[F, List[Message]](Nil)
    receivedMessagesSemaphore <- Semaphore[F](1)
  } yield new SqsConsumer[F, M](queueUrl, outstandingMessagesRef, receivedMessagesRef, receivedMessagesSemaphore, messageRetentionPeriod, visibilityTimeout, cleanup)
}
