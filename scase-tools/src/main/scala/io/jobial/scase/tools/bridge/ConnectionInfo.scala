package io.jobial.scase.tools.bridge

import cats.implicits.catsSyntaxEq
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.tibrv.TibrvContext
import io.lemonlabs.uri.Uri

trait ConnectionInfo {
  def uri: Uri
  
  def canonicalUri: Uri

  def uriForDestination(source: ConnectionInfo) =
    if (destinationName.isEmpty)
      canonicalUri.toUrl.removeEmptyPathParts.addPathPart(source.destinationName)
    else
      canonicalUri

  def pathLen: Int

  val path = uri.path.parts.map(p => if (p === "") None else Some(p)).padTo(pathLen, None)
  
  val pathLast = path.last.getOrElse("")

  val host = uri.toUrl.hostOption.map(_.toString).filter(_ =!= "")
  
  val port = uri.toUrl.port
  
  def destinationName: String
}

class PulsarConnectionInfo(val uri: Uri) extends ConnectionInfo {

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
  
  def destinationName = topic
}

object PulsarConnectionInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("pulsar"))
      Right(new PulsarConnectionInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid Pulsar URI: $uri"))
}

class TibrvConnectionInfo(val uri: Uri) extends ConnectionInfo {

  def subjects = Seq(pathLast)

  def pathLen = 2
  
  def context = TibrvContext(
    host.getOrElse(TibrvContext().host),
    port.getOrElse(TibrvContext().port),
    path(0).orElse(TibrvContext().network),
    path(1).orElse(TibrvContext().service)
  )

  def canonicalUri = Uri.parse(s"tibrv://${context.host}:${context.port}/${context.network}/${context.service}/${subjects.mkString(";")}")
  
  def destinationName = pathLast
}

object TibrvConnectionInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("tibrv"))
      Right(new TibrvConnectionInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid TibRV URI: $uri"))
}

class ActiveMQConnectionInfo(val uri: Uri) extends ConnectionInfo {

  def destinationName = pathLast

  def destination = context.session.createQueue(destinationName)

  def pathLen = 1

  def context = ActiveMQContext(
    host.getOrElse(ActiveMQContext().host),
    port.getOrElse(ActiveMQContext().port)
  )

  def canonicalUri = Uri.parse(s"activemq://${context.host}:${context.port}/${destinationName}")
}

object ActiveMQConnectionInfo {

  def apply(uri: Uri) =
    if (uri.schemeOption === Some("activemq"))
      Right(new ActiveMQConnectionInfo(uri))
    else
      Left(new IllegalArgumentException(s"Not a valid ActiveMQ URI: $uri"))
}
