package io.jobial.scase.tibrv

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Timer
import cats.effect.concurrent.MVar
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.implicits._
import com.tibco.tibrv.Tibrv
import com.tibco.tibrv.TibrvDispatcher
import com.tibco.tibrv.TibrvListener
import com.tibco.tibrv.TibrvMsg
import com.tibco.tibrv.TibrvMsgCallback
import com.tibco.tibrv.TibrvQueue
import com.tibco.tibrv.TibrvRvdTransport
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import java.net.InetAddress
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

class TibrvConsumer[F[_] : Concurrent : Timer, M](
  receiveResult: MVar[IO, (TibrvListener, TibrvMsg)],
  subjects: Seq[String],
  subjectFilter: String => Boolean
)(implicit context: TibrvContext) extends DefaultMessageConsumer[F, M] with TibrvMsgCallback with Logging {

  lazy val rvListeners = {
    if (!Tibrv.isValid) Tibrv.open(Tibrv.IMPL_NATIVE)
    
    val eventQueue = new TibrvQueue
    eventQueue.setLimitPolicy(
      TibrvQueue.DISCARD_NONE,
      0, // max queue size
      0
    )

    val name = s"TibcoRVConsumer${System.identityHashCode(this)}"
    new TibrvDispatcher(s"${name}Dispatcher", eventQueue)

    val networkWithSemicolon = for {
      network <- context.network
    } yield
      if (network.startsWith(";"))
        network
      else
        s";$network"

    val transport = new TibrvRvdTransport(context.service.getOrElse(null), networkWithSemicolon.getOrElse(null), s"${context.host}:${context.port}")
    transport.setDescription(s"$name@" + InetAddress.getLocalHost.getHostName.toLowerCase)

    for {
      subject <- subjects
    } yield
      new TibrvListener(eventQueue, this, transport, subject, null)
  }

  def onMsg(listener: TibrvListener, message: TibrvMsg) = {
    if (subjectFilter(message.getSendSubject))
      receiveResult.put(listener -> message)
    else
      unit[IO]
  }.unsafeRunSync()

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      _ <- pure(rvListeners)
      (listener, tibrvMessage) <- timeout.map(t => liftIO(receiveResult.read).timeout(t)).getOrElse(liftIO(receiveResult.read)).handleErrorWith {
        case t: TimeoutException =>
          trace(s"Receive timed out after $timeout in $this") >>
            raiseError(ReceiveTimeout(timeout, t))
        case t =>
          raiseError(t)
      }
      _ <- liftIO(receiveResult.take)
      _ <- trace(s"received message ${tibrvMessage.toString.take(200)} on $listener")
      message = Unmarshaller[M].unmarshal(tibrvMessage.getAsBytes)
      result <- message match {
        case Right(message) =>
          pure(DefaultMessageReceiveResult[F, M](
            pure(message),
            Map(),
            commit = unit,
            rollback = unit,
            underlyingMessageProvided = pure(tibrvMessage),
            underlyingContextProvided = pure(listener)
          ))
        case Left(error) =>
          raiseError(error)
      }
    } yield result

  override def toString = s"${super.toString} subjects: ${subjects}"

  def stop =
    delay(rvListeners.map(_.destroy()))
}

object TibrvConsumer extends CatsUtils with Logging {

  def apply[F[_] : Concurrent : Timer, M](
    subjects: Seq[String] = Seq("_LOCAL.>"),
    subjectFilter: String => Boolean = { _ => true }
  )(
    implicit context: TibrvContext,
    ioConcurrent: Concurrent[IO]
  ) =
    for {
      receiveResult <- liftIO(MVar.empty[IO, (TibrvListener, TibrvMsg)])(Concurrent[F])
    } yield new TibrvConsumer[F, M](receiveResult, subjects, subjectFilter)
}