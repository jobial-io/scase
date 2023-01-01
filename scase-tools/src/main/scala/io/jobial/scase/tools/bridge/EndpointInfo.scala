package io.jobial.scase.tools.bridge

import cats.implicits.catsSyntaxEq
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.tibrv.TibrvContext
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.UrlPath
import cats.implicits._
import io.jobial.scase.util.EitherUtil

object EndpointInfo {

  def apply(uri: Uri): Either[IllegalArgumentException, EndpointInfo] =
    if (uri.schemeOption === Some("pulsar"))
      Right(new PulsarEndpointInfo(uri))
    else if (uri.schemeOption === Some("tibrv"))
      Right(new TibrvEndpointInfo(uri))
    else if (uri.schemeOption === Some("activemq"))
      Right(new ActiveMQEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid ActiveMQ URI: $uri"))
}

trait EndpointInfo {

  def uri: Uri

  def canonicalUri: Uri

  def forSource(source: EndpointInfo) =
    if (destinationName.isEmpty)
      withDestinationName(source.destinationName)
    else
      this

  def withDestinationName(destinationName: String) = {
    val destinationPath = UrlPath.parse(destinationName)
    EndpointInfo.apply(canonicalUri.toUrl.withPath(UrlPath(canonicalUri.path.parts.dropRight(destinationPath.parts.size) ++ destinationPath.parts))).toOption.get
  }

  def pathLen: Int

  val path = uri.path.parts.map(p => if (p === "") None else Some(p)).padTo(pathLen, None)

  val pathLast = path.last.getOrElse("")

  val host = uri.toUrl.hostOption.map(_.toString).filter(_ =!= "")

  val port = uri.toUrl.port

  def destinationName = pathLast
}

class PulsarEndpointInfo(val uri: Uri) extends EndpointInfo {

  def topic = pathLast

  def topicPattern = topic.r

  def pathLen = 3

  def context = PulsarContext(
    host.getOrElse(PulsarContext().host),
    port.getOrElse(PulsarContext().port),
    path(0).getOrElse(PulsarContext().tenant),
    path(1).getOrElse(PulsarContext().namespace)
  )

  def canonicalUri = Uri.parse(s"pulsar://${context.host}:${context.port}/${context.tenant}/${context.namespace}/${topic}")
}

object PulsarEndpointInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("pulsar"))
      Right(new PulsarEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid Pulsar URI: $uri"))
}

class TibrvEndpointInfo(val uri: Uri) extends EndpointInfo {

  def subjects = Seq(pathLast)

  def pathLen = 2

  def context = TibrvContext(
    host.getOrElse(TibrvContext().host),
    port.getOrElse(TibrvContext().port),
    path(0).orElse(TibrvContext().network),
    path(1).orElse(TibrvContext().service)
  )

  def canonicalUri = Uri.parse(s"tibrv://${context.host}:${context.port}/${context.network}/${context.service}/${subjects.mkString(";")}")
}

object TibrvEndpointInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("tibrv"))
      Right(new TibrvEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid TibRV URI: $uri"))
}

class ActiveMQEndpointInfo(val uri: Uri) extends EndpointInfo {

  def destination = context.session.createQueue(destinationName)

  def pathLen = 1

  def context = ActiveMQContext(
    host.getOrElse(ActiveMQContext().host),
    port.getOrElse(ActiveMQContext().port)
  )

  def canonicalUri = Uri.parse(s"activemq://${context.host}:${context.port}/${destinationName}")
}

object ActiveMQEndpointInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("activemq"))
      Right(new ActiveMQEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid ActiveMQ URI: $uri"))
}
