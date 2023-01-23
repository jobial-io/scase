package io.jobial.scase.tools.bridge

import cats.implicits.catsSyntaxEq
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.tibrv.TibrvContext
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.UrlPath
import cats.implicits._

object EndpointInfo {

  def apply(uri: Uri): Either[IllegalArgumentException, EndpointInfo] =
    if (uri.schemeOption === Some("pulsar"))
      Right(PulsarEndpointInfo(uri))
    else if (uri.schemeOption === Some("tibrv"))
      Right(TibrvEndpointInfo(uri))
    else if (uri.schemeOption === Some("activemq"))
      Right(ActiveMQEndpointInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid endpoint URI: $uri"))
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

  val pathLast = path.lastOption.flatten.getOrElse("")

  val host = uri.toUrl.hostOption.map(_.toString).filter(_ =!= "")

  val port = uri.toUrl.port

  val destinationName = pathLast
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
}

case class TibrvEndpointInfo(uri: Uri) extends EndpointInfo {

  val subjects = Seq(pathLast)

  val pathLen = 2

  val context = TibrvContext(
    host.getOrElse(TibrvContext().host),
    port.getOrElse(TibrvContext().port),
    path.get(0).flatten.orElse(TibrvContext().network),
    path.get(1).flatten.orElse(TibrvContext().service)
  )

  val canonicalUri = Uri.parse(s"tibrv://${context.host}:${context.port}/${context.network}/${context.service}/${subjects.mkString(";")}")
}

case class ActiveMQEndpointInfo(uri: Uri) extends EndpointInfo {

  lazy val destination = context.session.createQueue(destinationName)

  val pathLen = 1

  val context = ActiveMQContext(
    host.getOrElse(ActiveMQContext().host),
    port.getOrElse(ActiveMQContext().port)
  )

  val canonicalUri = Uri.parse(s"activemq://${context.host}:${context.port}/${destinationName}")
}
