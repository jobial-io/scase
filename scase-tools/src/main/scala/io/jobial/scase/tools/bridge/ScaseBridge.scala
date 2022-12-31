package io.jobial.scase.tools.bridge

import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.ioEffect.whenA
import cats.effect.IO.raiseError
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import com.tibco.tibrv.TibrvMsg
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
import io.jobial.scase.tools.bridge
import io.jobial.scase.util.Cache
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.Message
import java.lang.System.currentTimeMillis
import javax.jms.Session
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object ScaseBridge extends CommandLineApp with ConnectionInfoParser with Logging {

  def run =
    command.description(""""
Forward requests and one-way messages from one transport to another.
""") {
      for {
        source <- opt[ConnectionInfo]("source", "s").required
          .description("tibrv://<subject> or pulsar://<topic> or activemq://<destination>")
        destination <- opt[ConnectionInfo]("destination", "d").required
          .description("tibrv:// or pulsar:// or activemq://, no subject or topic should be specified to select destination " +
            "based on the source topic or subject")
        protocol <- opt[String]("protocol", "p").default("TibrvMsg")
          .description("The marshalling protocol to use: currently TibrvMsg and Serialization are supported")
        oneWay <- opt[Boolean]("one-way", "o").default(false)
          .description("Forward one-way only messages - the default is request-response only")
        timeout <- opt[FiniteDuration]("timeout", "t").default(300.seconds)
          .description("Request timeout in seconds, unless specified by the client and the transport supports it (TibRV does not)")
      } yield for {
        marshalling <- source match {
          case source: ActiveMQConnectionInfo =>
            delay(new SerializationMarshalling[Any])
          case _ =>
            destination match {
              case destination: ActiveMQConnectionInfo =>
                delay(new SerializationMarshalling[Any])
              case _ =>
                delay(new TibrvMsgRawMarshalling().asInstanceOf[Marshalling[Any]])
            }
        }
        bridgeContext <- BridgeContext(source, destination, oneWay, timeout)(marshalling)
        bridge <- bridgeContext.runBridge
        r <- bridge.join
      } yield r
    }

  case class BridgeContext[M: Marshalling](
    source: ConnectionInfo,
    destination: ConnectionInfo,
    oneWay: Boolean,
    timeout: FiniteDuration,
    requestResponseClientCache: Cache[IO, String, RequestResponseClient[IO, M, M]],
    senderClientCache: Cache[IO, String, SenderClient[IO, M]],
    forwarderBridgeServiceState: Deferred[IO, ForwarderBridgeServiceState[IO]],
    requestResponseBridgeServiceState: Deferred[IO, RequestResponseBridgeServiceState[IO]],
    statLastTime: Ref[IO, Long]
  ) {

    val marshalling = Marshalling[M]

    def withPulsarContext[T](connectionInfo: ConnectionInfo)(f: PulsarContext => IO[T]) =
      connectionInfo match {
        case connectionInfo: PulsarConnectionInfo =>
          f(connectionInfo.context)
        case _ =>
          raiseError(new IllegalStateException("Pulsar context is required"))
      }

    def withSourcePulsarContext[T](f: PulsarContext => IO[T]) =
      withPulsarContext(source)(f)

    def withDestinationPulsarContext[T](f: PulsarContext => IO[T]) =
      withPulsarContext(destination)(f)

    def withTibrvContext[T](connectionInfo: ConnectionInfo)(f: TibrvContext => IO[T]) =
      connectionInfo match {
        case connectionInfo: TibrvConnectionInfo =>
          f(connectionInfo.context)
        case _ =>
          raiseError(new IllegalStateException("TibRV context is required"))
      }

    def withSourceTibrvContext[T](f: TibrvContext => IO[T]) =
      withTibrvContext(source)(f)

    def withDestinationTibrvContext[T](f: TibrvContext => IO[T]) =
      withTibrvContext(destination)(f)

    def withJMSSession[T](connectionInfo: ConnectionInfo)(f: Session => IO[T]) =
      connectionInfo match {
        case connectionInfo: ActiveMQConnectionInfo =>
          f(connectionInfo.context.session)
        case _ =>
          raiseError(new IllegalStateException("ActiveMQ context is required"))
      }

    def withSourceJMSSession[T](f: Session => IO[T]) =
      withJMSSession(source)(f)

    def withDestinationJMSSession[T](f: Session => IO[T]) =
      withJMSSession(destination)(f)

    def runBridge =
      startBridge[M](this)

  }

  object BridgeContext {

    def apply[M: Marshalling](
      source: ConnectionInfo,
      destination: ConnectionInfo,
      oneWay: Boolean,
      timeout: FiniteDuration
    ): IO[BridgeContext[M]] =
      for {
        requestResponseClientCache <- Cache[IO, String, RequestResponseClient[IO, M, M]](5.minutes)
        senderClientCache <- Cache[IO, String, SenderClient[IO, M]](5.minutes)
        forwarderBridgeState <- Deferred[IO, ForwarderBridgeServiceState[IO]]
        requestResponseBridgeState <- Deferred[IO, RequestResponseBridgeServiceState[IO]]
        statLastTime <- Ref.of[IO, Long](currentTimeMillis)
      } yield BridgeContext[M](source, destination, oneWay, timeout, requestResponseClientCache,
        senderClientCache, forwarderBridgeState, requestResponseBridgeState, statLastTime)
  }

  implicit def requestResponseMapping[M] = new RequestResponseMapping[M, M] {}

  def startBridge[M](implicit context: BridgeContext[M]) = {
    implicit val marshalling = context.marshalling

    if (context.oneWay)
      for {
        sourceClient <- clientForSource
        destinationClient <- clientForDestination
        state <- startForwarderBridge(sourceClient, destinationClient)
        _ <- context.forwarderBridgeServiceState.complete(state)
      } yield state
    else
      for {
        requestResponseSource <- serviceForSource
        requestResponseClient <- requestResponseClientForDestination
        state <- startRequestResponseBridge(requestResponseSource, requestResponseClient, context.timeout)
        _ <- context.requestResponseBridgeServiceState.complete(state)
      } yield state
  }

  def stripUriScheme(uri: String) = uri.substring(uri.indexOf("://") + 3)

  def serviceForSource[M: Marshalling](implicit context: BridgeContext[M]) =
    context.source match {
      case source: PulsarConnectionInfo =>
        context.withSourcePulsarContext { implicit context =>
          delay(PulsarServiceConfiguration.requestResponse[M, M](Right(source.topicPattern)).service[IO](_))
        }
      case source: TibrvConnectionInfo =>
        context.withSourceTibrvContext { implicit context =>
          delay(TibrvServiceConfiguration.requestResponse[M, M](source.subjects).service[IO](_))
        }
      case source: ActiveMQConnectionInfo =>
        context.withSourceJMSSession { implicit session =>
          delay(JMSServiceConfiguration.requestResponse[M, M](source.uri.toString, source.destination).service[IO](_))
        }
      case _ =>
        raiseError(new IllegalStateException(s"${context.source} not supported"))
    }

  def substituteDestinationName(source: String, destination: String, sourceName: String): String =
    sourceName.replaceAll(source, destination)

  def substituteDestinationName[M](sourceName: String)(implicit context: BridgeContext[M]): String = {
    val destinationUri = context.destination.uriForDestination(context.source).toString
      
    val r = substituteDestinationName(context.source.canonicalUri.toString, destinationUri, sourceName)
    r.substring(r.lastIndexOf('/') + 1)
  }

  def getDestinationName[M](r: MessageReceiveResult[IO, M])(implicit context: BridgeContext[M]) =
    for {
      message <- r.underlyingMessage[Any]
    } yield message match {
      case m: Message[_] =>
        val topicName = m.getTopicName
        val sourceName = s"pulsar://${context.source.asInstanceOf[bridge.PulsarConnectionInfo].context.host}:${context.source.asInstanceOf[bridge.PulsarConnectionInfo].context.port}/" + topicName.replace("persistent://", "")
        val r = substituteDestinationName(sourceName)
        Some(r)
      case m: TibrvMsg =>
        Some(substituteDestinationName(m.getSendSubject))
      case m: javax.jms.Message =>
        Some(substituteDestinationName(stripUriScheme(m.getJMSDestination.toString)))
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
          requestTimeoutCount <- bridge.requestTimeoutCount
          errorCount <- bridge.errorCount
          filteredRequestCount <- bridge.filteredRequestCount
          filteredResponseCount <- bridge.filteredResponseCount
          _ <- whenA(requestCount % 1000 === 0)(
            for {
              statLastTime <- context.statLastTime.modify(t => (currentTimeMillis, t))
              rate = 1000d / (currentTimeMillis - statLastTime) * 1000d
              _ <- info[IO](f"Processed ${requestCount} requests and ${responseCount} responses (${requestTimeoutCount} timeouts, ${errorCount} errors, ${filteredRequestCount} requests filtered, ${filteredResponseCount} responses filtered) ${rate}%.2f/s")
            } yield ()
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

  def requestResponseClientForDestination[M: Marshalling](implicit context: BridgeContext[M]) =
    context.destination match {
      case destination: PulsarConnectionInfo =>
        context.withDestinationPulsarContext { implicit pulsarContext =>
          createRequestResponseClient(d =>
            info[IO](s"Creating Pulsar request-response client for $d") >>
              PulsarServiceConfiguration.requestResponse[M, M](d).client[IO]
          )
        }
      case destination: TibrvConnectionInfo =>
        context.withDestinationTibrvContext { implicit tibrvContext =>
          createRequestResponseClient(d =>
            info[IO](s"Creating Tibrv request-response client for $d") >>
              TibrvServiceConfiguration.requestResponse[M, M](Seq(d)).client[IO]
          )
        }
      case destination: ActiveMQConnectionInfo =>
        context.withDestinationJMSSession { implicit session =>
          createRequestResponseClient(d =>
            info[IO](s"Creating JMS request-response client for $d") >>
              JMSServiceConfiguration.requestResponse[M, M](d, session.createQueue(d)).client[IO]
          )
        }
      case _ =>
        raiseError(new IllegalStateException(s"${context.destination} not supported"))
    }

  def clientForSource[M: Marshalling](implicit context: BridgeContext[M]) =
    context.source match {
      case source: PulsarConnectionInfo =>
        context.withSourcePulsarContext { implicit context =>
          PulsarServiceConfiguration.source[M](Right(source.topicPattern)).client[IO]
        }
      case source: TibrvConnectionInfo =>
        context.withSourceTibrvContext { implicit context =>
          TibrvServiceConfiguration.source[M](source.subjects).client[IO]
        }
      case source: ActiveMQConnectionInfo =>
        context.withSourceJMSSession { implicit session =>
          JMSServiceConfiguration.source[M](source.destination).client[IO]
        }
      case _ =>
        raiseError(new IllegalStateException(s"${context.source} not supported"))
    }

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
            for {
              statLastTime <- context.statLastTime.modify(t => (currentTimeMillis, t))
              rate = 1000d / (currentTimeMillis - statLastTime) * 1000d
              _ <- info[IO](f"Processed ${messageCount} messages (${errorCount} errors, ${filteredMessageCount} filtered) ${rate}%.2f/s")
            } yield ()
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

  def clientForDestination[M: Marshalling](implicit context: BridgeContext[M]) =
    context.destination match {
      case destination: PulsarConnectionInfo =>
        context.withDestinationTibrvContext { implicit tibrvContext =>
          createSenderClient(d => TibrvServiceConfiguration.destination[M](d).client[IO])
        }
      case destination: TibrvConnectionInfo =>
        context.withDestinationPulsarContext { implicit pulsarContext =>
          createSenderClient(d => PulsarServiceConfiguration.destination[M](d).client[IO])
        }
      case destination: ActiveMQConnectionInfo =>
        context.withDestinationJMSSession { implicit session =>
          createSenderClient(d => JMSServiceConfiguration.destination[M](session.createQueue(d)).client[IO])
        }
      case _ =>
        raiseError(new IllegalStateException(s"${context.destination} not supported"))
    }

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
