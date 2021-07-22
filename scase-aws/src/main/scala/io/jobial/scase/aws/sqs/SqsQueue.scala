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
  implicit override val awsContext: AwsContext
) extends Queue[F, M]
  with SqsClient
  with Logging {

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

  def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]] = {
    logger.debug(s"subscribed with callback $callback to queue $queueUrl")

    val cancelledRef = Ref.of[F, Boolean](false)
    val outstandingMessagesRef = Ref.of[F, collection.Map[M, String]](identityTrieMap[M, String])

    for {
      cancelled <- cancelledRef
      outstandingMessages <- outstandingMessagesRef
      subscription = new MessageSubscription[F, M] {
        def receiveMessages: F[_] =
          for {
            c <- cancelled.get
            _ <-
              if (!c) {
                //Concurrent[F].unit
                (for {
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
                        _ <- outstandingMessages.update(_ + ((unmarshalledMessage, sqsMessage.getReceiptHandle)))
                        r <- callback(
                          MessageReceiveResult(
                            message = unmarshalledMessage,
                            // TODO: add standard attributes returned by getAttributes...
                            attributes = sqsMessage.getMessageAttributes.asScala.toMap.filter(e => Option(e._2.getStringValue).isDefined).mapValues(_.getStringValue).toMap,
                            commit = { () =>
                              // TODO: this is ugly
                              for {
                                o <- outstandingMessages.get
                                r <- o.get(unmarshalledMessage) match {
                                  case Some(receiptHandle) =>
                                    Concurrent[F].delay(deleteMessage(queueUrl, receiptHandle))
                                  case _ =>
                                    Concurrent[F].raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                                }
                                _ <- outstandingMessages.update(_ - unmarshalledMessage)
                              } yield println("committed")
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
                  _ <- receiveMessages
                } yield ()) handleErrorWith { t =>
                  logger.error(s"failed to receive messages", t)
                  Concurrent[F].raiseError(t)
                }
              }
              else Concurrent[F].unit
          } yield ()

        val join = receiveMessages

        def cancel =
          cancelled.set(true)

        def isCancelled =
          cancelled.get
      }
      // TODO: make this an implicit
      f <- subscription.receiveMessages //.start(IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool())))
      _ = println(f)
    } yield subscription
  }


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

object SqsQueue extends SqsClient {

  def create[F[_] : LiftIO : Monad, M](
    name: String,
    messageRetentionPeriod: Option[Duration] = Some(1.hour),
    visibilityTimeout: Option[Duration] = Some(10.minutes),
    cleanup: Boolean = false
  )(
    implicit awsContext: AwsContext
  ) =
    for {
      queueUrl <- createQueueIfNotExists(name).to[F]
    } yield SqsQueue[F, M](
      queueUrl,
      name,
      messageRetentionPeriod,
      visibilityTimeout,
      cleanup
    )
}