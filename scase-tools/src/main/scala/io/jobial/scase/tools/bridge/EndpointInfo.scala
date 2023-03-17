package io.jobial.scase.tools.bridge

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import cats.implicits.catsSyntaxEq
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.jms.JMSServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.UrlPath
import org.apache.pulsar.client.api.SubscriptionInitialPosition

import java.time.Instant
import javax.jms.Session

object EndpointInfo extends CatsUtils with Logging {

  def apply(uri: Uri): Either[IllegalArgumentException, EndpointInfo] =
    if (uri.schemeOption === Some("pulsar"))
      Right(PulsarEndpointInfo(uri))
    else if (uri.schemeOption === Some("tibrv"))
      Right(TibrvEndpointInfo(uri))
    else if (uri.schemeOption === Some("activemq"))
      Right(ActiveMQEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid endpoint URI: $uri"))

  // TODO: move these somewhere else
  def clientForDestination[F[_] : Concurrent : Timer, M: Marshaller](destination: EndpointInfo, actualDestination: String): F[_ <: SenderClient[F, M]] =
    destination match {
      case destination: TibrvEndpointInfo =>
        destination.withTibrvContext { implicit tibrvContext =>
          TibrvServiceConfiguration.destination[M](actualDestination).client[F]
        }
      case destination: PulsarEndpointInfo =>
        destination.withPulsarContext { implicit pulsarContext =>
          PulsarServiceConfiguration.destination[M](actualDestination).client[F]
        }
      case destination: ActiveMQEndpointInfo =>
        destination.withJMSSession { implicit session =>
          JMSServiceConfiguration.destination[M](session.createQueue(actualDestination)).client[F]
        }
      case _ =>
        raiseError(new IllegalStateException(s"${destination} not supported"))
    }

  def clientForDestination[F[_] : Concurrent : Timer, M: Marshaller](destination: EndpointInfo): F[_ <: SenderClient[F, M]] =
    clientForDestination[F, M](destination, destination.destinationName)
}

trait EndpointInfo extends CatsUtils {

  def uri: Uri

  def canonicalUri: Uri

  def asSourceUriString = (
    if (pathLast === "")
      withDestinationName(".*")
    else this
    ).canonicalUri.toStringRaw

  def asDestinationUriString(actualSource: EndpointInfo) = (if (pathLast === "") withDestinationName(actualSource.pathLast) else this).canonicalUri.toStringRaw

  def withDestinationName(destinationName: String) = {
    val destinationPath = UrlPath.parse(destinationName)
    EndpointInfo.apply(canonicalUri.toUrl.withPath(UrlPath(canonicalUri.path.parts.dropRight(destinationPath.parts.size) ++ destinationPath.parts))).toOption.get
  }

  def pathLen: Int

  val path = uri.path.parts.map(p => if (p === "") None else Some(p)).padTo(pathLen, None)

  val pathLast = path.lastOption.flatten.getOrElse("")

  val host = uri.toUrl.hostOption.map(_.toString).filter(_ =!= "")

  val port = uri.toUrl.port

  val destinationName = pathLast

  def asPulsarEndpointInfo =
    this match {
      case endpointInfo: PulsarEndpointInfo =>
        Right(endpointInfo)
      case _ =>
        Left(new IllegalStateException("Not a Pulsar endpoint"))
    }

  def withPulsarContext[F[_] : Concurrent, T](f: PulsarContext => F[T]): F[T] =
    for {
      e <- fromEither(asPulsarEndpointInfo)
      r <- e.withPulsarContext(f)
    } yield r

  def asTibrvEndpointInfo =
    this match {
      case endpointInfo: TibrvEndpointInfo =>
        Right(endpointInfo)
      case _ =>
        Left(new IllegalStateException("Not a TibRV endpoint"))
    }

  def withTibrvContext[F[_] : Concurrent, T](f: TibrvContext => F[T]): F[T] =
    for {
      e <- fromEither(asTibrvEndpointInfo)
      r <- e.withTibrvContext(f)
    } yield r

  def asActiveMQEndpointInfo =
    this match {
      case endpointInfo: ActiveMQEndpointInfo =>
        Right(endpointInfo)
      case _ =>
        Left(new IllegalStateException("ActiveMQ context is required"))
    }

  def withJMSSession[F[_] : Concurrent, T](f: Session => F[T]): F[T] =
    for {
      e <- fromEither(asActiveMQEndpointInfo)
      r <- e.withJMSSession(f)
    } yield r
}

case class PulsarEndpointInfo(uri: Uri) extends EndpointInfo {

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

case class TibrvEndpointInfo(uri: Uri) extends EndpointInfo {

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

case class ActiveMQEndpointInfo(uri: Uri) extends EndpointInfo {

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
