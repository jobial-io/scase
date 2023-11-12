package io.jobial.scase.tools.endpoint

import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.implicits._
import cats.implicits.catsSyntaxEq
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.jms.JMSServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.UrlPath
import org.apache.pulsar.client.api.ConsumerBuilder
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.apache.pulsar.client.api.SubscriptionInitialPosition.Earliest

import java.time.Instant
import java.util.UUID.randomUUID
import javax.jms.Session
import scala.concurrent.duration.DurationInt

object Endpoint extends CatsUtils with Logging {

  def apply(uri: Uri): Either[IllegalArgumentException, Endpoint] =
    if (uri.schemeOption === Some("pulsar"))
      Right(PulsarEndpoint(uri))
    else if (uri.schemeOption === Some("tibrv"))
      Right(TibrvEndpoint(uri))
    else if (uri.schemeOption === Some("activemq"))
      Right(ActiveMQEndpoint(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid endpoint URI: $uri"))

  // TODO: move these somewhere else
  def destinationClient[F[_] : Concurrent : Timer, M: Marshaller](destination: Endpoint, actualDestination: String): F[_ <: SenderClient[F, M]] =
    destination match {
      case destination: TibrvEndpoint =>
        destination.withTibrvContext { implicit tibrvContext: TibrvContext =>
          TibrvServiceConfiguration.destination[M](actualDestination).client[F]
        }
      case destination: PulsarEndpoint =>
        destination.withPulsarContext { implicit pulsarContext: PulsarContext =>
          PulsarServiceConfiguration.destination[M](actualDestination).client[F]
        }
      case destination: ActiveMQEndpoint =>
        destination.withJMSSession { implicit session: Session =>
          JMSServiceConfiguration.destination[M](session.createQueue(actualDestination)).client[F]
        }
      case _ =>
        raiseError(new IllegalStateException(s"${destination} not supported"))
    }

  def destinationClient[F[_] : Concurrent : Timer, M: Marshaller](destination: Endpoint): F[_ <: SenderClient[F, M]] =
    destinationClient[F, M](destination, destination.destinationName)

  val defaultSubscriptionInitialPosition = Some(Earliest)

  def defaultSubscriptionInitialPublishTime = Some(Instant.now.minusSeconds(60))

  def handlerService[F[_] : Concurrent : Timer, M: Marshaller : Unmarshaller](source: Endpoint, messageHandler: MessageHandler[F, M])(implicit ioContextShift: ContextShift[IO]) =
    source match {
      case source: PulsarEndpoint =>
        source.withPulsarContext { implicit pulsarContext: PulsarContext =>
          PulsarServiceConfiguration.handler[M](
            Right(source.topicPattern),
            Some(1.second),
            source.subscriptionInitialPosition.orElse(defaultSubscriptionInitialPosition),
            source.subscriptionInitialPublishTime.orElse(defaultSubscriptionInitialPublishTime),
            source.subscriptionName.getOrElse(s"subscription-${randomUUID}"),
            identity[ConsumerBuilder[Array[Byte]]](_) // TODO: fix this
          ).service[F](messageHandler)
        }
      case source: TibrvEndpoint =>
        source.withTibrvContext { implicit tibrvContext: TibrvContext =>
          TibrvServiceConfiguration.handler[M](source.subjects).service[F](messageHandler)
        }
      case source: ActiveMQEndpoint =>
        source.withJMSSession { implicit session: Session =>
          JMSServiceConfiguration.handler[M]("", source.destination).service[F](messageHandler)
        }
      case _ =>
        raiseError(new IllegalStateException(s"${source} not supported"))
    }

}

trait Endpoint extends CatsUtils {

  def uri: Uri

  def canonicalUri: Uri

  def asSourceUriString = (
    if (pathLast === "")
      withDestinationName(".*")
    else this
    ).canonicalUri.toStringRaw

  def asDestinationUriString(actualSource: Endpoint) = (if (pathLast === "") withDestinationName(actualSource.pathLast) else this).canonicalUri.toStringRaw

  def withDestinationName(destinationName: String) = {
    val destinationPath = UrlPath.parse(destinationName)
    Endpoint.apply(canonicalUri.toUrl.withPath(UrlPath(canonicalUri.path.parts.dropRight(destinationPath.parts.size) ++ destinationPath.parts))).toOption.get
  }

  def pathLen: Int

  val path = uri.path.parts.map(p => if (p === "") None else Some(p)).padTo(pathLen, None)

  val pathLast = path.lastOption.flatten.getOrElse("")

  val host = uri.toUrl.hostOption.map(_.toString).filter(_ =!= "")

  val port = uri.toUrl.port

  val destinationName = pathLast

  def asPulsarEndpoint =
    this match {
      case endpoint: PulsarEndpoint =>
        Right(endpoint)
      case _ =>
        Left(new IllegalStateException("Not a Pulsar endpoint"))
    }

  def withPulsarContext[F[_] : Concurrent, T](f: PulsarContext => F[T]): F[T] =
    for {
      e <- fromEither(asPulsarEndpoint)
      r <- e.withPulsarContext(f)
    } yield r

  def asTibrvEndpoint =
    this match {
      case endpoint: TibrvEndpoint =>
        Right(endpoint)
      case _ =>
        Left(new IllegalStateException("Not a TibRV endpoint"))
    }

  def withTibrvContext[F[_] : Concurrent, T](f: TibrvContext => F[T]): F[T] =
    for {
      e <- fromEither(asTibrvEndpoint)
      r <- e.withTibrvContext(f)
    } yield r

  def asActiveMQEndpoint =
    this match {
      case endpoint: ActiveMQEndpoint =>
        Right(endpoint)
      case _ =>
        Left(new IllegalStateException("ActiveMQ context is required"))
    }

  def withJMSSession[F[_] : Concurrent, T](f: Session => F[T]): F[T] =
    for {
      e <- fromEither(asActiveMQEndpoint)
      r <- e.withJMSSession(f)
    } yield r
}

case class PulsarEndpoint(uri: Uri) extends Endpoint {

  val topic = pathLast

  val topicPattern = topic.r

  val pathLen = 3

  val context = PulsarContext(
    host.getOrElse(PulsarContext().host),
    port.getOrElse(PulsarContext().port),
    path.get(0).flatten.getOrElse(PulsarContext().tenant),
    path.get(1).flatten.getOrElse(PulsarContext().namespace)
  )

  val canonicalUri = Uri.parse(s"pulsar://${context.host}:${context.port}/${context.tenant}/${context.namespace}/${topic}")

  val subscriptionName = uri.toUrl.query.param("subscriptionName")

  val subscriptionInitialPosition = uri.toUrl.query.param("subscriptionInitialPosition").map(SubscriptionInitialPosition.valueOf)

  val subscriptionInitialPublishTime = uri.toUrl.query.param("subscriptionInitialPublishTime").map(Instant.parse)

  def withPulsarContext[F[_], T](f: PulsarContext => F[T]): F[T] =
    f(context)
}

case class TibrvEndpoint(uri: Uri) extends Endpoint {

  val subjects = Seq(pathLast.replaceAll("\\.\\*$", ".>"))

  val pathLen = 2

  val context = TibrvContext(
    host.getOrElse(TibrvContext().host),
    port.getOrElse(TibrvContext().port),
    path.get(0).flatten.orElse(TibrvContext().network),
    path.get(1).flatten.orElse(TibrvContext().service)
  )

  val canonicalUri = Uri.parse(s"tibrv://${context.host}:${context.port}/${context.network.getOrElse("")}/${context.service.getOrElse("")}/${subjects.mkString(";")}")

  override def asSourceUriString = super.asSourceUriString.replaceAll("\\.>$", ".*")

  def withTibrvContext[F[_], T](f: TibrvContext => F[T]): F[T] =
    f(context)
}

case class ActiveMQEndpoint(uri: Uri) extends Endpoint {

  lazy val destination = context.session.createQueue(destinationName)

  val pathLen = 1

  val context = ActiveMQContext(
    host.getOrElse(ActiveMQContext().host),
    port.getOrElse(ActiveMQContext().port)
  )

  val canonicalUri = Uri.parse(s"activemq://${context.host}:${context.port}/${destinationName}")

  def withJMSSession[F[_], T](f: Session => F[T]) =
    f(context.session)

}
