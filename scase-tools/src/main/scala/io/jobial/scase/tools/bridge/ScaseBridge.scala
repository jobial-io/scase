package io.jobial.scase.tools.bridge

import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.ioEffect.whenA
import cats.effect.IO.raiseError
import cats.effect.concurrent.Deferred
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
import io.jobial.scase.core.impl.ForwarderBridgeServiceState
import io.jobial.scase.core.impl.RequestResponseBridge
import io.jobial.scase.core.impl.RequestResponseBridge.destinationBasedOnSourceRequest
import io.jobial.scase.core.impl.RequestResponseBridge.requestResponseOnlyFilter
import io.jobial.scase.core.impl.RequestResponseBridgeServiceState
import io.jobial.scase.jms.JMSServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshalling
import io.jobial.scase.marshalling.Marshalling._
import io.jobial.scase.marshalling.serialization.SerializationMarshalling
import io.jobial.scase.marshalling.tibrv.raw.TibrvMsgRawMarshalling
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.util.Cache
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.Message
import javax.jms.Session
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object ScaseBridge extends CommandLineApp with ContextParsers with Logging {

  def run =
    command.description(""""
Forward requests and one-way messages from one transport to another.
""") {
      for {
        source <- opt[String]("source", "s").required
          .description("tibrv://<subject> or pulsar://<topic> or jms://<destination>")
        destination <- opt[String]("destination", "d").required
          .description("tibrv:// or pulsar:// or jms://, no subject or topic should be specified to select destination " +
            "based on the source topic or subject")
        protocol <- opt[String]("protocol", "p").default("TibrvMsg")
          .description("The marshalling protocol to use: currently TibrvMsg and Serialization are supported")
        oneWay <- opt[Boolean]("one-way", "o").default(false)
          .description("Forward one-way only messages - the default is request-response only")
        timeout <- opt[FiniteDuration]("timeout", "t").default(300.seconds)
          .description("Request timeout in seconds, unless specified by the client and the transport supports it (TibRV does not)")
        tibrvContext <- opt[TibrvContext]("tibrv-context")
          .description("host:port:network:service")
        pulsarContext <- opt[PulsarContext]("pulsar-context")
          .description("host:port:tenant:namespace")
        activemqContext <- opt[ActiveMQContext]("activemq-context")
          .description("host:port:transacted:acknowledge_mode")
      } yield for {
        bridgeContext <-
          if (activemqContext.isDefined)
            BridgeContext[Any](tibrvContext, pulsarContext, activemqContext)(new SerializationMarshalling[Any])
          else
            BridgeContext[TibrvMsg](tibrvContext, pulsarContext, activemqContext)(new TibrvMsgRawMarshalling)
        bridge <- bridgeContext.runBridge(source, destination, oneWay, timeout)
        r <- bridge.join
      } yield r
    }

  val pulsarScheme = "pulsar://"
  val tibrvScheme = "tibrv://"
  val jmsScheme = "jms://"

  case class BridgeContext[M: Marshalling](
    tibrvContext: Option[TibrvContext],
    pulsarContext: Option[PulsarContext],
    activemqContext: Option[ActiveMQContext],
    requestResponseClientCache: Cache[IO, String, RequestResponseClient[IO, M, M]],
    senderClientCache: Cache[IO, String, SenderClient[IO, M]],
    forwarderBridgeServiceState: Deferred[IO, ForwarderBridgeServiceState[IO]],
    requestResponseBridgeServiceState: Deferred[IO, RequestResponseBridgeServiceState[IO]]
  ) {

    val marshalling = Marshalling[M]

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

    def runBridge(source: String, destination: String, oneWay: Boolean, timeout: FiniteDuration) =
      startBridge[M](source, destination, oneWay, timeout)(this)

  }

  object BridgeContext {

    def apply[M: Marshalling](
      tibrvContext: Option[TibrvContext] = None,
      pulsarContext: Option[PulsarContext] = None,
      activemqContext: Option[ActiveMQContext] = None
    ): IO[BridgeContext[M]] =
      for {
        requestResponseClientCache <- Cache[IO, String, RequestResponseClient[IO, M, M]](5.minutes)
        senderClientCache <- Cache[IO, String, SenderClient[IO, M]](5.minutes)
        forwarderBridgeState <- Deferred[IO, ForwarderBridgeServiceState[IO]]
        requestResponseBridgeState <- Deferred[IO, RequestResponseBridgeServiceState[IO]]
      } yield BridgeContext[M](tibrvContext, pulsarContext, activemqContext, requestResponseClientCache,
        senderClientCache, forwarderBridgeState, requestResponseBridgeState)
  }

  implicit def requestResponseMapping[M] = new RequestResponseMapping[M, M] {}

  def startBridge[M](source: String, destination: String, oneWay: Boolean, timeout: FiniteDuration)(implicit context: BridgeContext[M]) = {
    implicit val marshalling = context.marshalling

    if (oneWay)
      for {
        sourceClient <- clientForSource(source)
        destinationClient <- clientForDestination(destination)
        state <- startForwarderBridge(sourceClient, destinationClient)
        _ <- context.forwarderBridgeServiceState.complete(state)
      } yield state
    else
      for {
        requestResponseSource <- serviceForSource(source)
        requestResponseClient <- requestResponseClientForDestination(destination)
        state <- startRequestResponseBridge(requestResponseSource, requestResponseClient, timeout)
        _ <- context.requestResponseBridgeServiceState.complete(state)
      } yield state
  }

  def stripUriScheme(uri: String) = uri.substring(uri.indexOf("://") + 3)

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
      case m: javax.jms.Message =>
        Some(stripUriScheme(m.getJMSDestination.toString))
      case _ =>
        None
    }

  def createRequestResponseClient[M](f: String => IO[RequestResponseClient[IO, M, M]])(implicit context: BridgeContext[M]) =
    delay {
      r: MessageReceiveResult[IO, M] =>
        for {
          d <- getDestinationName(r)
          state <- context.requestResponseBridgeServiceState.get
          bridge = state.service
          requestCount <- bridge.requestCount
          responseCount <- bridge.responseCount
          errorCount <- bridge.errorCount
          filteredRequestCount <- bridge.filteredRequestCount
          filteredResponseCount <- bridge.filteredResponseCount
          _ <- whenA(requestCount % 1000 === 0)(
            info[IO](s"Processed ${requestCount} requests and ${responseCount} responses (${errorCount} errors, ${filteredRequestCount} requests filtered, ${filteredResponseCount} responses filtered)")
          )
          _ <- trace[IO](s"Forwarding request to $d")
          client <- d match {
            case Some(d) =>
              for {
                client <- context.requestResponseClientCache.getOrCreate(d, f(d), { (destination, client) =>
                  info[IO](s"Stopping request-response client for destination $destination") >>
                    client.stop
                })
              } yield Some(client)
            case None =>
              IO(None)
          }
        } yield client
    }

  def requestResponseClientForDestination[M: Marshalling](destination: String)(implicit context: BridgeContext[M]) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit tibrvContext =>
        createRequestResponseClient(d =>
          info[IO](s"Creating Tibrv request-response client for $d") >>
            TibrvServiceConfiguration.requestResponse[M, M](Seq(d)).client[IO]
        )
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit pulsarContext =>
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
        PulsarServiceConfiguration.source[M](Right(stripUriScheme(source).r)).client[IO]
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        TibrvServiceConfiguration.source[M](Seq(stripUriScheme(source))).client[IO]
      }
    else if (source.startsWith(jmsScheme))
      context.withJMSSession { implicit session =>
        JMSServiceConfiguration.source[M](session.createQueue(stripUriScheme(source))).client[IO]
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def createSenderClient[M](f: String => IO[SenderClient[IO, M]])(implicit context: BridgeContext[M]) =
    delay {
      r: MessageReceiveResult[IO, M] =>
        for {
          d <- getDestinationName(r)
          state <- context.forwarderBridgeServiceState.get
          bridge = state.service
          messageCount <- bridge.messageCount
          errorCount <- bridge.errorCount
          filteredMessageCount <- bridge.filteredMessageCount
          _ <- whenA(messageCount % 1000 === 0)(
            info[IO](s"Processed ${messageCount} messages (${errorCount} errors, ${filteredMessageCount} filtered)")
          )
          client <- d match {
            case Some(d) =>
              for {
                client <- context.senderClientCache.getOrCreate(d, f(d), { (destination, client) =>
                  info[IO](s"Stopping client for destination $destination") >>
                    client.stop
                })
              } yield Some(client)
            case None =>
              IO(None)
          }
        } yield client
    }

  def clientForDestination[M: Marshalling](destination: String)(implicit context: BridgeContext[M]) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit tibrvContext =>
        createSenderClient(d => TibrvServiceConfiguration.destination[M](d).client[IO])
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit pulsarContext =>
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
    destination: MessageReceiveResult[IO, M] => IO[Option[RequestResponseClient[IO, M, M]]],
    timeout: FiniteDuration
  )(
    implicit mapping: RequestResponseMapping[M, M]
  ) =
    for {
      bridge <- RequestResponseBridge(source, destinationBasedOnSourceRequest(destination, timeout), requestResponseOnlyFilter[IO, M])
      serviceState <- bridge.start
    } yield serviceState.asInstanceOf[RequestResponseBridgeServiceState[IO]]

  def startForwarderBridge[M: Marshalling](
    sourceClient: ReceiverClient[IO, M],
    destinationClient: MessageReceiveResult[IO, M] => IO[Option[SenderClient[IO, M]]]
  ) =
    for {
      bridge <- ForwarderBridge(sourceClient, destinationBasedOnSource(destinationClient), oneWayOnlyFilter[IO, M])
      serviceState <- bridge.start
    } yield serviceState.asInstanceOf[ForwarderBridgeServiceState[IO]]
}
