package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.Ref
import cats.implicits._
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.duration.DurationInt

class ForwarderBridge[F[_] : TemporalEffect, REQ: Unmarshaller, RESP: Marshaller](
  source: ReceiverClient[F, REQ],
  destination: MessageReceiveResult[F, RESP] => F[Option[MessageSendResult[F, RESP]]],
  filter: MessageReceiveResult[F, REQ] => F[Option[MessageReceiveResult[F, RESP]]],
  val stopped: Ref[F, Boolean],
  messageCounter: Ref[F, Long],
  sentMessageCounter: Ref[F, Long],
  errorCounter: Ref[F, Long],
  filteredMessageCounter: Ref[F, Long],
  maximumPendingMessages: Int
) extends DefaultService[F] with CatsUtils with Logging {

  val receiveTimeout = 1.second

  def continueForwarding =
    for {
      stopped <- stopped.get
      r <- whenA(!stopped)(forward)
    } yield r

  def forward: F[Unit] =
    for {
      receiveResult <- source.receiveWithContext(receiveTimeout) onError {
        case t: ReceiveTimeout =>
          continueForwarding
        case t: Throwable =>
          errorCounter.update(_ + 1) >>
            error(s"Error while receiving in $this") >>
            continueForwarding
      }
      _ <- (for {
        _ <- start(continueForwarding)
        _ <- messageCounter.update(_ + 1)
        messageCount <- messageCount
        errorCount <- errorCount
        filteredMessageCount <- filteredMessageCount
        sentMessageCount <- sentMessageCount
        pendingMessages = messageCount - errorCount - filteredMessageCount - sentMessageCount
        _ <- whenA(pendingMessages > maximumPendingMessages)(
          error("Dropping message (rolling back if supported) because of slow or failing destination") >>
            raiseError(MessageDropException)
        )
        filteredReceiveResult <- filter(receiveResult)
        _ <- filteredReceiveResult match {
          case Some(filteredReceiveResult) =>
            destination(filteredReceiveResult) >>
              sentMessageCounter.update(_ + 1)
          case None =>
            filteredMessageCounter.update(_ + 1)
        }
      } yield ()) handleErrorWith { case t =>
        errorCounter.update(_ + 1) >>
          receiveResult.rollback >>
          error(s"Error while forwarding in $this")
      }
    } yield ()

  def start =
    start(forward) >> pure(new ForwarderBridgeServiceState(this))

  def messageCount = messageCounter.get

  def sentMessageCount = sentMessageCounter.get

  def errorCount = errorCounter.get

  def filteredMessageCount = filteredMessageCounter.get
}

class ForwarderBridgeServiceState[F[_]: TemporalEffect](bridge: ForwarderBridge[F, _, _]) extends ServiceState[F]
  with CatsUtils with Logging {

  def stop = bridge.stopped.set(true) >> pure(this)

  def join: F[ServiceState[F]] = waitFor(bridge.stopped.get)(pure(_)) >> pure(this)

  def service = bridge
}

object ForwarderBridge extends CatsUtils with Logging {

  def apply[F[_] : TemporalEffect, M: Unmarshaller : Marshaller](
    source: ReceiverClient[F, M],
    destination: MessageReceiveResult[F, M] => F[Option[MessageSendResult[F, M]]],
    filter: MessageReceiveResult[F, M] => F[Option[MessageReceiveResult[F, M]]],
    maximumPendingMessages: Int
  ) =
    for {
      stopped <- Ref.of[F, Boolean](false)
      messageCounter <- Ref.of[F, Long](0)
      sentMessageCounter <- Ref.of[F, Long](0)
      errorCounter <- Ref.of[F, Long](0)
      filteredMessageCounter <- Ref.of[F, Long](0)
    } yield
      new ForwarderBridge[F, M, M](source, destination, filter, stopped, messageCounter, sentMessageCounter, errorCounter, filteredMessageCounter, maximumPendingMessages)

  def fixedDestination[F[_] : Concurrent, M](destination: SenderClient[F, M]) = { r: MessageReceiveResult[F, M] =>
    for {
      message <- r.message
      sendResult <- destination.send(message)(SendMessageContext(r.attributes))
    } yield Option(sendResult)
  }

  def destinationBasedOnSource[F[_] : Concurrent, M](destination: MessageReceiveResult[F, M] => F[Option[SenderClient[F, M]]]) = { r: MessageReceiveResult[F, M] =>
    for {
      message <- r.message
      d <- destination(r)
      sendResult <- d.map(d => d.send(message)(SendMessageContext(r.attributes))).toList.sequence
    } yield sendResult.headOption
  }

  def allowAllFilter[F[_] : ConcurrentEffect, M] = { r: MessageReceiveResult[F, M] =>
    pure(Option(r))
  }

  def oneWayOnlyFilter[F[_] : ConcurrentEffect, M]: MessageReceiveResult[F, M] => F[Option[MessageReceiveResult[F, M]]] = { r: MessageReceiveResult[F, M] =>
    if (r.responseProducerId.isDefined)
      pure(None)
    else
      pure(Option(r))
  }
}

// TODO: rename this
case object MessageDropException extends IllegalStateException
