package io.jobial.scase.aws.sqs

import cats.Traverse
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, IO, Sync}
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.aws.client.identitymap.identityTrieMap
import io.jobial.scase.core._
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Consumer implementation for AWS SQS.
 */
class SqsConsumer[F[_], M](
  queueUrl: String,
  outstandingMessagesRef: Ref[F, collection.Map[M, String]],
  val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]],
  messageRetentionPeriod: Option[Duration],
  visibilityTimeout: Option[Duration],
  cleanup: Boolean
)(
  implicit val awsContext: AwsContext
) extends DefaultMessageConsumer[F, M]
  with Logging {

  import awsContext.sqsClient._

  override def initialize(implicit concurrent: Concurrent[F]) =
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
      _ = logger.debug(s"created queue $queueUrl")
      _ <- messageRetentionPeriod.map(setMessageRetentionPeriod(queueUrl, _)).getOrElse(IO())
      _ <- visibilityTimeout.map(setVisibilityTimeout(queueUrl, _)).getOrElse(IO())
    } yield ())
  
  //  _ <- concurrent.liftIO(initialize)
  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]) = {
    logger.debug(s"subscribed with callback $callback to queue $queueUrl")

    for {
      // TODO: set visibility timeout to 0 here to allow other clients receiving uncorrelated messages
      messages <- Concurrent[F].delay {
        logger.debug(s"waiting for messages on $queueUrl")
        receiveMessage(queueUrl, 10, 1).getMessages
      }
      _ <- {
        logger.debug(s"received messages $messages on queue $queueUrl")

        Traverse[List].sequence(messages.asScala.toList.map { sqsMessage =>
          //                        try {
          for {
            unmarshalledMessage <- Concurrent[F].fromEither(u.unmarshalFromText(sqsMessage.getBody))
            _ <- outstandingMessagesRef.update(_ + ((unmarshalledMessage, sqsMessage.getReceiptHandle)))
            r <- callback(
              MessageReceiveResult(
                message = unmarshalledMessage,
                // TODO: add standard attributes returned by getAttributes...
                attributes = sqsMessage.getMessageAttributes.asScala.toMap.filter(e => Option(e._2.getStringValue).isDefined).mapValues(_.getStringValue).toMap,
                commit = { () =>
                  for {
                    o <- outstandingMessagesRef.get
                    r <- o.get(unmarshalledMessage) match {
                      case Some(receiptHandle) =>
                        Concurrent[F].delay(deleteMessage(queueUrl, receiptHandle))
                      case _ =>
                        Concurrent[F].raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                    }
                    _ <- outstandingMessagesRef.update(_ - unmarshalledMessage)
                  } yield ()
                },
                rollback = { () =>
                  ???
                  //                                outstandingMessages.remove(unmarshalledMessage) match {
                  //                                  case Some(receiptHandle) =>
                  //                                    // if the process fails at this point it will still roll back after the visibility timeout
                  //                                    IO(changeMessageVisibility(queueUrl, receiptHandle, 0))
                  //                                  case _ =>
                  //                                    IO.raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                  //                                }
                }
              )
            )
            //                        } catch {
            //                          case t: Throwable =>
            //                            // TODO: add retry limit and a limited cache for failed messages to avoid retrying over and over again
            //                            logger.error(s"could not process received message $sqsMessage", t)
            //                        }
          } yield r
        })
      }
    } yield ()
    //    ) handleErrorWith { t =>
    //      logger.error(s"failed to receive messages", t)
    //      Concurrent[F].raiseError(t)
    //    }


  }
}

object SqsConsumer {

  def apply[F[_] : Sync, M](
    queueUrl: String,
    messageRetentionPeriod: Option[Duration] = Some(1.hour),
    visibilityTimeout: Option[Duration] = Some(10.minutes),
    cleanup: Boolean = false
  )(
    implicit awsContext: AwsContext
  ): F[SqsConsumer[F, M]] = for {
    subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    outstandingMessagesRef <- Ref.of[F, collection.Map[M, String]](identityTrieMap[M, String])
  } yield new SqsConsumer[F, M](queueUrl, outstandingMessagesRef, subscriptions, messageRetentionPeriod, visibilityTimeout, cleanup)
}
