package io.jobial.scase.tools.bridge

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.raiseError
import cats.effect.IO.whenA
import cats.effect.Ref
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
import io.jobial.scase.util.Cache
import io.jobial.sclap.CommandLineApp
import io.lemonlabs.uri.Uri
import org.apache.pulsar.client.api.Message
import java.lang.System.currentTimeMillis
import java.util.UUID.randomUUID
import javax.jms.Session
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object ScaseBridge extends CommandLineApp with EndpointInfoParser with Logging {

  def run =
    command.description(""""
Forward requests and one-way messages from one transport to another.
""") {
      for {
        source <- opt[EndpointInfo]("source", "s").required
          .description("""The uri for the source. Supported schemes: tibrv://, pulsar://, activemq://.
          
Patterns with matching groups are supported.

Examples:

Options: --source=pulsar://host:6650/tenant/namespace/xxx.* --destination=tibrv://host:7500/network/service
Actual source: pulsar://host:6650/tenant/namespace/xxx.yyy
Resulting destination: tibrv://host:7500/network/service/xxx.yyy

Options: --source=pulsar://host:6650/tenant/namespace/xxx.* --destination=tibrv://host:7500/network/service/zzz
Actual source: pulsar://host:6650/tenant/namespace/xxx.yyy
Resulting destination: tibrv://host:7500/network/service/zzz

Options: --source=tibrv://host:7500/network/service/xxx.> --destination=pulsar://host:6650/tenant/namespace
Actual source: tibrv://host:7500/network/service/xxx.yyy
Resulting destination: pulsar://host:6650/tenant/namespace/xxx.yyy

Options: --source=tibrv://host:7500/network/service/xxx.* --destination=pulsar://host:6650/tenant/namespace/
Actual source: tibrv://host:7500/network/service/xxx.yyy
Resulting destination: pulsar://host:6650/tenant/namespace/xxx.yyy

Options: --source=pulsar://host:6650/tenant/namespace/([A-Z].*)\\.prod\\.(.*) --destination=tibrv://host:7500/network/service/$1.dev.$2
Actual source: pulsar://host:6650/tenant/namespace/xxx.prod.yyy
Resulting destination: tibrv://host:7500/network/service/xxx.dev.yyy

Also see --destination.""")
        destination <- opt[EndpointInfo]("destination", "d").required
          .description("""The uri for the destination. Supported schemes: tibrv://, pulsar://, activemq://. 
If no subject or topic is specified, it will be copied from the source. Back references to pattern matching groups in --source are supported.
            
See --source for details on pattern matching and substitution examples.""")
        protocol <- opt[String]("protocol", "p").default("TibrvMsg")
          .description("The marshalling protocol to use: currently TibrvMsg and Serialization are supported")
        oneWay <- opt[Boolean]("one-way", "o").default(false)
          .description("Forward one-way only messages - the default is request-response only")
        timeout <- opt[FiniteDuration]("timeout", "t").default(300.seconds)
          .description("Request timeout in seconds, unless specified by the client and the transport supports it (TibRV does not)")
        maxPendingMessages <- opt[Int]("max-pending-messages").default(3000)
          .description("The maximum number of pending messages before the bridge starts dropping and rolling back")
      } yield for {
        marshalling <- source match {
          case source: ActiveMQEndpointInfo =>
            delay(new SerializationMarshalling[Any])
          case _ =>
            destination match {
              case destination: ActiveMQEndpointInfo =>
                delay(new SerializationMarshalling[Any])
              case _ =>
                delay(new TibrvMsgRawMarshalling().asInstanceOf[Marshalling[Any]])
            }
        }
        bridgeContext <- BridgeContext(source, destination, oneWay, timeout, maxPendingMessages)(marshalling)
        bridge <- bridgeContext.runBridge
        r <- bridge.join
      } yield r
    }

  case class BridgeContext[M: Marshalling](
    source: EndpointInfo,
    destination: EndpointInfo,
    oneWay: Boolean,
    timeout: FiniteDuration,
    maximumPendingMessages: Int,
    requestResponseClientCache: Cache[IO, String, RequestResponseClient[IO, M, M]],
    senderClientCache: Cache[IO, String, SenderClient[IO, M]],
    forwarderBridgeServiceState: Deferred[IO, ForwarderBridgeServiceState[IO]],
    requestResponseBridgeServiceState: Deferred[IO, RequestResponseBridgeServiceState[IO]],
    statLastTime: Ref[IO, Long],
    statLastCount: Ref[IO, Long]
  ) {

    val marshalling = Marshalling[M]

    def withPulsarContext[T](endpointInfo: EndpointInfo)(f: PulsarContext => IO[T]) =
      endpointInfo match {
        case endpointInfo: PulsarEndpointInfo =>
          f(endpointInfo.context)
        case _ =>
          raiseError(new IllegalStateException("Pulsar context is required"))
      }

    def withSourcePulsarContext[T](f: PulsarContext => IO[T]) =
      withPulsarContext(source)(f)

    def withDestinationPulsarContext[T](f: PulsarContext => IO[T]) =
      withPulsarContext(destination)(f)

    def withTibrvContext[T](endpointInfo: EndpointInfo)(f: TibrvContext => IO[T]) =
      endpointInfo match {
        case endpointInfo: TibrvEndpointInfo =>
          f(endpointInfo.context)
        case _ =>
          raiseError(new IllegalStateException("TibRV context is required"))
      }

    def withSourceTibrvContext[T](f: TibrvContext => IO[T]) =
      withTibrvContext(source)(f)

    def withDestinationTibrvContext[T](f: TibrvContext => IO[T]) =
      withTibrvContext(destination)(f)

    def withJMSSession[T](endpointInfo: EndpointInfo)(f: Session => IO[T]) =
      endpointInfo match {
        case endpointInfo: ActiveMQEndpointInfo =>
          f(endpointInfo.context.session)
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
      source: EndpointInfo,
      destination: EndpointInfo,
      oneWay: Boolean,
      timeout: FiniteDuration,
      maximumPendingMessages: Int = 3000
    ): IO[BridgeContext[M]] =
      for {
        requestResponseClientCache <- Cache[IO, String, RequestResponseClient[IO, M, M]](5.minutes)
        senderClientCache <- Cache[IO, String, SenderClient[IO, M]](5.minutes)
        forwarderBridgeState <- Deferred[IO, ForwarderBridgeServiceState[IO]]
        requestResponseBridgeState <- Deferred[IO, RequestResponseBridgeServiceState[IO]]
        statLastTime <- Ref.of[IO, Long](currentTimeMillis)
        statLastCount <- Ref.of[IO, Long](0)
      } yield BridgeContext[M](source, destination, oneWay, timeout, maximumPendingMessages, requestResponseClientCache,
        senderClientCache, forwarderBridgeState, requestResponseBridgeState, statLastTime, statLastCount)
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
      case source: PulsarEndpointInfo =>
        context.withSourcePulsarContext { implicit context =>
          delay(PulsarServiceConfiguration.requestResponse[M, M](Right(source.topicPattern), subscriptionName = source.subscriptionName.getOrElse(defaultPulsarSubscriptionName)).service[IO](_))
        }
      case source: TibrvEndpointInfo =>
        context.withSourceTibrvContext { implicit context =>
          delay(TibrvServiceConfiguration.requestResponse[M, M](source.subjects).service[IO](_))
        }
      case source: ActiveMQEndpointInfo =>
        context.withSourceJMSSession { implicit session =>
          delay(JMSServiceConfiguration.requestResponse[M, M](source.uri.toString, source.destination).service[IO](_))
        }
      case _ =>
        raiseError(new IllegalStateException(s"${context.source} not supported"))
    }

  def substituteDestinationName(source: EndpointInfo, destination: EndpointInfo, actualSource: EndpointInfo): EndpointInfo =
    EndpointInfo.apply(Uri.parse(actualSource.uri.toStringRaw.replaceAll(
      source.asSourceUriString,
      destination.asDestinationUriString(actualSource)
    ))).toOption.get

  def substituteDestinationName[M](actualSource: EndpointInfo)(implicit context: BridgeContext[M]): String = {
    val destinationForSource = context.destination.forSource(context.source)

    val r = substituteDestinationName(context.source, destinationForSource, actualSource)
    r.destinationName
  }

  def substituteDestinationName[M](destinationName: String)(implicit context: BridgeContext[M]): String =
    substituteDestinationName(context.source.withDestinationName(destinationName))

  def getDestinationName[M](r: MessageReceiveResult[IO, M])(implicit context: BridgeContext[M]) =
    for {
      message <- r.underlyingMessage[Any]
    } yield message match {
      case m: Message[_] =>
        val topicName = m.getTopicName
        val r = substituteDestinationName(topicName.replace("persistent://", ""))
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
              statLastCount <- context.statLastCount.modify(c => (requestCount, c))
              t = currentTimeMillis
              rate = (requestCount - statLastCount).toDouble / (t - statLastTime) * 1000d
              _ <- whenA(t > statLastTime && requestCount > statLastCount)(
                info[IO](f"Processed ${requestCount} requests and ${responseCount} responses (${requestTimeoutCount} timeouts, ${errorCount} errors, ${filteredRequestCount} requests filtered, ${filteredResponseCount} responses filtered) ${rate}%.2f/s")
              )
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
    createRequestResponseClient(d =>
      info[IO](s"Creating request-response client for $d for ${context.destination.uri}") >> {
        context.destination match {
          case destination: PulsarEndpointInfo =>
            context.withDestinationPulsarContext { implicit pulsarContext =>
              PulsarServiceConfiguration.requestResponse[M, M](d).client[IO]
            }
          case destination: TibrvEndpointInfo =>
            context.withDestinationTibrvContext { implicit tibrvContext =>
              TibrvServiceConfiguration.requestResponse[M, M](Seq(d)).client[IO]
            }
          case destination: ActiveMQEndpointInfo =>
            context.withDestinationJMSSession { implicit session =>
              JMSServiceConfiguration.requestResponse[M, M](d, session.createQueue(d)).client[IO]
            }
          case _ =>
            raiseError(new IllegalStateException(s"${context.destination} not supported"))
        }
      }
    )

  val defaultPulsarSubscriptionName = s"scase-bridge-${randomUUID}"

  def clientForSource[M: Marshalling](implicit context: BridgeContext[M]) =
    context.source match {
      case source: PulsarEndpointInfo =>
        context.withSourcePulsarContext { implicit context =>
          PulsarServiceConfiguration.source[M](Right(source.topicPattern), source.subscriptionName.getOrElse(defaultPulsarSubscriptionName)).client[IO]
        }
      case source: TibrvEndpointInfo =>
        context.withSourceTibrvContext { implicit context =>
          TibrvServiceConfiguration.source[M](source.subjects).client[IO]
        }
      case source: ActiveMQEndpointInfo =>
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
              statLastCount <- context.statLastCount.modify(c => (messageCount, c))
              t = currentTimeMillis
              rate = (messageCount - statLastCount).toDouble / (t - statLastTime) * 1000d
              _ <- whenA(t > statLastTime && messageCount > statLastCount)(
                info[IO](f"Processed ${messageCount} messages (${errorCount} errors, ${filteredMessageCount} filtered) ${rate}%.2f/s")
              )
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
    createSenderClient(d =>
      info[IO](s"Creating sender client for $d for destination ${context.destination.uri}") >> {
        context.destination match {
          case destination: TibrvEndpointInfo =>
            context.withDestinationTibrvContext { implicit tibrvContext =>
              TibrvServiceConfiguration.destination[M](d).client[IO]
            }
          case destination: PulsarEndpointInfo =>
            context.withDestinationPulsarContext { implicit pulsarContext =>
              PulsarServiceConfiguration.destination[M](d).client[IO]
            }
          case destination: ActiveMQEndpointInfo =>
            context.withDestinationJMSSession { implicit session =>
              JMSServiceConfiguration.destination[M](session.createQueue(d)).client[IO]
            }
          case _ =>
            raiseError(new IllegalStateException(s"${context.destination} not supported"))
        }
      }
    )

  def startRequestResponseBridge[M: Marshalling](
    source: RequestHandler[IO, M, M] => IO[Service[IO]],
    destination: MessageReceiveResult[IO, M] => IO[Option[RequestResponseClient[IO, M, M]]],
    timeout: FiniteDuration
  )(
    implicit mapping: RequestResponseMapping[M, M],
    context: BridgeContext[M]
  ) =
    for {
      bridge <- RequestResponseBridge(source, destinationBasedOnSourceRequest(destination, timeout), requestResponseOnlyFilter[IO, M], context.maximumPendingMessages)
      serviceState <- bridge.start
    } yield serviceState.asInstanceOf[RequestResponseBridgeServiceState[IO]]

  def startForwarderBridge[M: Marshalling](
    sourceClient: ReceiverClient[IO, M],
    destinationClient: MessageReceiveResult[IO, M] => IO[Option[SenderClient[IO, M]]]
  )(implicit context: BridgeContext[M]) =
    for {
      bridge <- ForwarderBridge(sourceClient, destinationBasedOnSource(destinationClient), oneWayOnlyFilter[IO, M], context.maximumPendingMessages)
      serviceState <- bridge.start
    } yield serviceState.asInstanceOf[ForwarderBridgeServiceState[IO]]
}
