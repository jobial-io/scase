package io.jobial.scase.aws.sqs

import java.util.concurrent.atomic.AtomicBoolean

import cats.effect.IO
import io.jobial.scase.aws.util.identitymap.identityTrieMap
import io.jobial.scase.aws.util.{AwsContext, S3Client, SqsClient}
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._

/**
 * Queue implementation using AWS SQS.
 */
case class SqsQueue[M](
  name: String,
  messageRetentionPeriod: Option[Duration] = Some(1 hour),
  visibilityTimeout: Option[Duration] = Some(10 minutes),
  cleanup: Boolean = false
)(
  implicit val awsContext: AwsContext
  //executionContext: ExecutionContext
) extends Queue[M]
  with SqsClient
  with S3Client
  with Logging {

  val queueUrl = createQueueIfNotExists(name).get // TODO: get rid of this by wrapping it in a proper constructor

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

  def subscribe[T](callback: MessageReceiveResult[M] => IO[T])(implicit u: Unmarshaller[M]): IO[MessageSubscription[M]] = IO {
    logger.debug(s"subscribed with callback $callback to queue $queueUrl")

    new MessageSubscription[M] {
      val cancelled = new AtomicBoolean

      def receiveMessages: IO[_] =
        if (!cancelled.get) {
          (for {
            // TODO: set visibility timeout to 0 here to allow other clients receiving uncorrelated messages
            messages <- IO {
              logger.debug(s"waiting for messages on $queueUrl")
              receiveMessage(queueUrl, 10, 1).getMessages
            }
            r <- {
              logger.debug(s"received messages $messages on queue $queueUrl")

              messages.asScala.map { sqsMessage =>
                IO {
                  try {
                    val unmarshalledMessage = u.unmarshalFromText(sqsMessage.getBody)
                    outstandingMessages += ((unmarshalledMessage, sqsMessage.getReceiptHandle))
                    callback(
                      MessageReceiveResult(
                        message = unmarshalledMessage,
                        // TODO: add standard attributes returned by getAttributes...
                        attributes = sqsMessage.getMessageAttributes.asScala.toMap.filter(e => Option(e._2.getStringValue).isDefined).mapValues(_.getStringValue),
                        commit = { () =>
                          outstandingMessages.remove(unmarshalledMessage) match {
                            case Some(receiptHandle) =>
                              IO(deleteMessage(queueUrl, receiptHandle))
                            case _ =>
                              IO.raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                          }
                        },
                        rollback = { () =>
                          outstandingMessages.remove(unmarshalledMessage) match {
                            case Some(receiptHandle) =>
                              // if the process fails at this point it will still roll back after the visibility timeout
                              IO(changeMessageVisibility(queueUrl, receiptHandle, 0))
                            case _ =>
                              IO.raiseError(CouldNotFindMessageToCommit(unmarshalledMessage))
                          }
                        }
                      )
                    )
                  } catch {
                    case t: Throwable =>
                      // TODO: add retry limit and a limited cache for failed messages to avoid retrying over and over again
                      logger.error(s"could not process received message $sqsMessage", t)
                  }
                }
              }
              receiveMessages
            }
          } yield r) handleErrorWith { t =>
            logger.error(s"failed to receive messages", t)
            IO.raiseError(t)
          }
        } else IO()

      val subscription = receiveMessages

      def cancel =
        IO(cancelled.set(true))

      def isCancelled =
        cancelled.get

      val outstandingMessages = identityTrieMap[M, String]
    }
  }

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]) = {
    logger.debug(s"sending to queue $queueUrl $message")
    val r: IO[MessageSendResult[M]] = IO(sendMessage(queueUrl, implicitly[Marshaller[M]].marshalToText(message), attributes)).flatMap {
      case Success(r) =>
        logger.debug(s"successfully sent to queue $queueUrl $message")
        IO(MessageSendResult[M]())
      case Failure(t) =>
        logger.error(s"failure sending to queue $queueUrl $message", t)
        IO.raiseError[MessageSendResult[M]](t)
    }

    r handleErrorWith { t =>
      logger.error(s"failed sending to queue $queueUrl $message", t)
      r
    }
  }


}