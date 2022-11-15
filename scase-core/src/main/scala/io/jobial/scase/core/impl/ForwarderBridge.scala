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
  stopped: Ref[F, Boolean]
) extends DefaultService[F] with CatsUtils with Logging {

  def continueForwarding =
    for {
      stopped <- stopped.get
      r <- whenA(!stopped)(forward)
    } yield r

  def forward: F[Unit] =
    (for {
      receiveResult <- source.receiveWithContext(1.second)
      filteredReceiveResult <- filter(receiveResult)
      sendResult <- filteredReceiveResult match {
        case Some(filteredReceiveResult) =>
          destination(filteredReceiveResult)
        case None =>
          pure(None)
      }
      r <- continueForwarding
    } yield r) handleErrorWith {
      case t: ReceiveTimeout =>
        continueForwarding
      case t: Throwable =>
        error(s"error while forwarding in $this", t) >>
          continueForwarding
    }

  def start: F[ServiceState[F]] =
    start(forward) >> pure(new ServiceState[F] {
      def stop = stopped.set(true) >> pure(this)

      def join: F[ServiceState[F]] = waitFor(stopped.get)(pure(_)) >> pure(this)

      def service = ForwarderBridge.this
    })
}

object ForwarderBridge extends CatsUtils with Logging {

  def apply[F[_] : TemporalEffect, M: Unmarshaller : Marshaller](
    source: ReceiverClient[F, M],
    destination: MessageReceiveResult[F, M] => F[Option[MessageSendResult[F, M]]],
    filter: MessageReceiveResult[F, M] => F[Option[MessageReceiveResult[F, M]]]
  ) =
    for {
      stopped <- Ref.of[F, Boolean](false)
    } yield
      new ForwarderBridge[F, M, M](source, destination, filter, stopped)

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