package io.jobial.scase.tools.bridge

import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.raiseError
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.Service
import io.jobial.scase.core.impl.ForwarderBridge
import io.jobial.scase.core.impl.ForwarderBridge.destinationBasedOnSource
import io.jobial.scase.core.impl.ForwarderBridge.oneWayOnlyFilter
import io.jobial.scase.core.impl.RequestResponseBridge
import io.jobial.scase.core.impl.RequestResponseBridge.destinationBasedOnSourceRequest
import io.jobial.scase.core.impl.RequestResponseBridge.requestResponseOnlyFilter
import io.jobial.scase.jms.JMSServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.javadsl.Marshalling
import io.jobial.scase.marshalling.serialization.javadsl.SerializationMarshalling
import io.jobial.scase.marshalling.tibrv.raw.javadsl.TibrvMsgRawMarshalling
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.util.Cache
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.Message
import javax.jms.Session

object ScaseBridge extends CommandLineApp with ContextParsers with Logging {

  def run =
    command.description("Forward requests and one-way messages from one transport to another") {
      for {
        source <- opt[String]("source", "s").required
          .description("tibrv://<subject> or pulsar://<topic> or jms://<destination>")
        destination <- opt[String]("destination", "d").required
          .description("tibrv:// or pulsar:// or jms://, no subject or topic should be specified to select destination " +
            "based on the source topic or subject")
        protocol <- opt[String]("protocol", "p").default("M")
          .description("The marshalling protocol to use: currently only M is supported")
        tibrvContext <- opt[TibrvContext]("tibrv-context")
          .description("host:port:network:service")
        pulsarContext <- opt[PulsarContext]("pulsar-context")
          .description("host:port:tenant:namespace")
        activemqContext <- opt[ActiveMQContext]("activemq-context")
          .description("host:port:transacted:acknowledge_mode")
      } yield for {
        bridgeContext <-
          if (activemqContext.isDefined)
            BridgeContext[Any](tibrvContext, pulsarContext, activemqContext, marshalling = new SerializationMarshalling)
          else
            BridgeContext[TibrvMsg](tibrvContext, pulsarContext, activemqContext, marshalling = new TibrvMsgRawMarshalling)
        (requestResponseBridge, forwarderBridge) <- bridgeContext.runBridge(source, destination)
        _ <- requestResponseBridge.join
        r <- forwarderBridge.join
      } yield r
    }

  val pulsarScheme = "pulsar://"
  val tibrvScheme = "tibrv://"
  val jmsScheme = "jms://"

  case class BridgeContext[M](
    tibrvContext: Option[TibrvContext],
    pulsarContext: Option[PulsarContext],
    activemqContext: Option[ActiveMQContext],
    requestResponseClientCache: Cache[IO, String, RequestResponseClient[IO, M, M]],
    senderClientCache: Cache[IO, String, SenderClient[IO, M]],
    marshalling: Marshalling[M]
  ) {

    def withPulsarContext[T](f: PulsarContext => IO[T]) =
      pulsarContext match {
        case Some(context) =>
          f(context)
        case None =>
          raiseError(new IllegalStateException("Pulsar context is required"))
      }

    def withTibrvContext[T](f: TibrvContext => IO[T]) =
      tibrvContext match {
        case Some(context) =>
          f(context)
        case None =>
          raiseError(new IllegalStateException("Tibrv context is required"))
      }

    def withJMSSession[T](f: Session => IO[T]) =
      activemqContext match {
        case Some(context) =>
          f(context.session)
        case None =>
          raiseError(new IllegalStateException("ActiveMQ context is required"))
      }

    def runBridge(source: String, destination: String) =
      startBridge[M](source, destination)(this)

  }

  object BridgeContext {

    def apply[M](
      tibrvContext: Option[TibrvContext],
      pulsarContext: Option[PulsarContext],
      activemqContext: Option[ActiveMQContext],
      marshalling: Marshalling[M]
    ): IO[BridgeContext[M]] = for {
      requestResponseClientCache <- Cache[IO, String, RequestResponseClient[IO, M, M]]
      senderClientCache <- Cache[IO, String, SenderClient[IO, M]]
    } yield BridgeContext[M](tibrvContext, pulsarContext, activemqContext, requestResponseClientCache, senderClientCache, marshalling)
  }

  implicit def requestResponseMapping[M] = new RequestResponseMapping[M, M] {}

  def startBridge[M](source: String, destination: String)(implicit context: BridgeContext[M]) = {
    implicit val marshalling = context.marshalling
    for {
      requestResponseSource <- serviceForSource(source)
      requestResponseClient <- requestResponseClientForDestination(destination)
      requestResponseBridge <- startRequestResponseBridge(requestResponseSource, requestResponseClient)
      sourceClient <- clientForSource(source)
      destinationClient <- clientForDestination(destination)
      forwarderBridge <- startForwarderBridge(sourceClient, destinationClient)
    } yield (requestResponseBridge, forwarderBridge)
  }

  def stripUriScheme(uri: String) = uri.substring(uri.indexOf("://") + 3)

  implicit def marshaller[M: Marshalling] = implicitly[Marshalling[M]].marshaller

  implicit def unmarshaller[M: Marshalling] = implicitly[Marshalling[M]].unmarshaller

  implicit def eitherMarshaller[M: Marshalling] = implicitly[Marshalling[M]].eitherMarshaller

  implicit def eitherUnmarshaller[M: Marshalling] = implicitly[Marshalling[M]].eitherUnmarshaller

  implicit def throwableMarshaller[M: Marshalling] = implicitly[Marshalling[M]].throwableMarshaller

  implicit def throwableUnmarshaller[M: Marshalling] = implicitly[Marshalling[M]].throwableUnmarshaller

  def sourceConfiguration[M: Marshalling](source: String)(implicit context: BridgeContext[M]) =
    if (source.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        delay(PulsarServiceConfiguration.requestResponse[M, M](Right(stripUriScheme(source).r)).service[IO](_))
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        delay(TibrvServiceConfiguration.requestResponse[M, M](Seq(stripUriScheme(source))).service[IO](_))
      }
    else if (source.startsWith(jmsScheme))
      context.withJMSSession { implicit session =>
        delay(JMSServiceConfiguration.requestResponse[M, M](source, session.createQueue(stripUriScheme(source))).service[IO](_))
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def serviceForSource[M: Marshalling](source: String)(implicit context: BridgeContext[M]) =
    if (source.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        delay(PulsarServiceConfiguration.requestResponse[M, M](Right(stripUriScheme(source).r)).service[IO](_))
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        delay(TibrvServiceConfiguration.requestResponse[M, M](Seq(stripUriScheme(source))).service[IO](_))
      }
    else if (source.startsWith(jmsScheme))
      context.withJMSSession { implicit session =>
        delay(JMSServiceConfiguration.requestResponse[M, M](source, session.createQueue(stripUriScheme(source))).service[IO](_))
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def getDestinationName[M](r: MessageReceiveResult[IO, M]) =
    for {
      message <- r.underlyingMessage[Any]
    } yield message match {
      case m: Message[_] =>
        val topicName = m.getTopicName
        Some(topicName.substring(topicName.lastIndexOf("/") + 1))
      case m: TibrvMsg =>
        Some(m.getSendSubject)
      case _ =>
        None
    }

  def createRequestResponseClient[M](f: String => IO[RequestResponseClient[IO, M, M]])(implicit context: BridgeContext[M]) =
    delay {
      r: MessageReceiveResult[IO, M] =>
        for {
          d <- getDestinationName(r)
          _ <- trace[IO](s"Forwarding request to $d")
          client <- d match {
            case Some(d) =>
              for {
                client <- context.requestResponseClientCache.getOrCreate(d, f(d))
              } yield Some(client)
            case None =>
              IO(None)
          }
        } yield client
    }

  def requestResponseClientForDestination[M: Marshalling](destination: String)(implicit context: BridgeContext[M]) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit c =>
        createRequestResponseClient(d =>
          info[IO](s"Creating Tibrv request-response client for $d") >>
            TibrvServiceConfiguration.requestResponse[M, M](Seq(d)).client[IO]
        )
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit c =>
        createRequestResponseClient(d =>
          info[IO](s"Creating Pulsar request-response client for $d") >>
            PulsarServiceConfiguration.requestResponse[M, M](d).client[IO]
        )
      }
    else if (destination.startsWith(jmsScheme))
      context.withJMSSession { implicit session =>
        createRequestResponseClient(d =>
          info[IO](s"Creating JMS request-response client for $d") >>
            JMSServiceConfiguration.requestResponse[M, M](d, session.createQueue(d)).client[IO]
        )
      }
    else
      raiseError(new IllegalStateException(s"${destination} not supported"))

  def clientForSource[M: Marshalling](source: String)(implicit context: BridgeContext[M]) =
    if (source.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        PulsarServiceConfiguration.source[M](Right(source.r)).client[IO]
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        TibrvServiceConfiguration.source[M](Seq(stripUriScheme(source))).client[IO]
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def createSenderClient[M](f: String => IO[SenderClient[IO, M]]) =
    delay {
      r: MessageReceiveResult[IO, M] =>
        for {
          d <- getDestinationName(r)
          r <- d.map(f).sequence
        } yield r
    }

  def clientForDestination[M: Marshalling](destination: String)(implicit context: BridgeContext[M]) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        createSenderClient(d => TibrvServiceConfiguration.destination[M](d).client[IO])
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        createSenderClient(d => PulsarServiceConfiguration.destination[M](d).client[IO])
      }
    else if (destination.startsWith(jmsScheme))
      context.withJMSSession { implicit session =>
        createSenderClient(d => JMSServiceConfiguration.destination[M](session.createQueue(d)).client[IO])
      }
    else
      raiseError(new IllegalStateException(s"${destination} not supported"))

  def startRequestResponseBridge[M: Marshalling](
    source: RequestHandler[IO, M, M] => IO[Service[IO]],
    destination: MessageReceiveResult[IO, M] => IO[Option[RequestResponseClient[IO, M, M]]]
  )(
    implicit mapping: RequestResponseMapping[M, M]
  ) =
    for {
      bridge <- RequestResponseBridge(source, destinationBasedOnSourceRequest(destination), requestResponseOnlyFilter[IO, M])
      serviceState <- bridge.start
    } yield serviceState

  def startForwarderBridge[M: Marshalling](
    sourceClient: ReceiverClient[IO, M],
    destinationClient: MessageReceiveResult[IO, M] => IO[Option[SenderClient[IO, M]]]
  ) =
    for {
      bridge <- ForwarderBridge(sourceClient, destinationBasedOnSource(destinationClient), oneWayOnlyFilter[IO, M])
      serviceState <- bridge.start
    } yield serviceState
}
