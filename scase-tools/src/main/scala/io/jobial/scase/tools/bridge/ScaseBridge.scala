package io.jobial.scase.tools.bridge

import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.raiseError
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
import io.jobial.scase.core.impl.RequestResponseBridge
import io.jobial.scase.core.impl.RequestResponseBridge.destinationBasedOnSourceRequest
import io.jobial.scase.core.impl.RequestResponseBridge.requestResponseOnlyFilter
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.tibrv.raw._
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.util.Cache
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.Message

object ScaseBridge extends CommandLineApp with ContextParsers with Logging {

  def run =
    command.description("Forward requests and one-way messages from one transport to another") {
      for {
        source <- opt[String]("source", "s").required
          .description("tibrv://<subject> or pulsar://<topic>")
        destination <- opt[String]("destination", "d").required
          .description("tibrv:// or pulsar://, no subject or topic should be specified to select destination " +
            "based on the source topic or subject")
        protocol <- opt[String]("protocol", "p").default("tibrvmsg")
          .description("The marshalling protocol to use: currently only tibrvmsg is supported")
        tibrvContext <- opt[TibrvContext]("tibrv-context")
          .description("host:port:network:service")
        pulsarContext <- opt[PulsarContext]("pulsar-context")
          .description("host:port:tenant:namespace")
      } yield for {
        bridgeContext <- BridgeContext(tibrvContext, pulsarContext)
        (requestResponseBridge, forwarderBridge) <- startBridge(source, destination)(bridgeContext)
        r <- requestResponseBridge.join
        r <- forwarderBridge.join
      } yield r
    }

  val pulsarScheme = "pulsar://"

  val tibrvScheme = "tibrv://"

  case class BridgeContext(
    tibrvContext: Option[TibrvContext],
    pulsarContext: Option[PulsarContext],
    requestResponseClientCache: Cache[IO, String, RequestResponseClient[IO, TibrvMsg, TibrvMsg]],
    senderClientCache: Cache[IO, String, SenderClient[IO, TibrvMsg]]
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
  }

  object BridgeContext {

    def apply(
      tibrvContext: Option[TibrvContext],
      pulsarContext: Option[PulsarContext]
    ): IO[BridgeContext] = for {
      requestResponseClientCache <- Cache[IO, String, RequestResponseClient[IO, TibrvMsg, TibrvMsg]]
      senderClientCache <- Cache[IO, String, SenderClient[IO, TibrvMsg]]
    } yield BridgeContext(tibrvContext, pulsarContext, requestResponseClientCache, senderClientCache)
  }

  def startBridge(source: String, destination: String)(implicit context: BridgeContext) =
    for {
      requestResponseSource <- serviceForSource(source)
      requestResponseClient <- requestResponseClientForDestination(destination)
      requestResponseBridge <- startRequestResponseBridge(requestResponseSource, requestResponseClient)
      sourceClient <- clientForSource(source)
      destinationClient <- clientForDestination(destination)
      forwarderBridge <- startForwarderBridge(sourceClient, destinationClient)
    } yield (requestResponseBridge, forwarderBridge)

  def stripUriScheme(uri: String) = uri.substring(uri.indexOf("://") + 3)

  def serviceForSource(source: String)(implicit context: BridgeContext) =
    if (source.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        delay(PulsarServiceConfiguration.requestResponse[TibrvMsg, TibrvMsg](Right(stripUriScheme(source).r)).service[IO](_))
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        delay(TibrvServiceConfiguration.requestResponse[TibrvMsg, TibrvMsg](Seq(stripUriScheme(source))).service[IO](_))
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def getDestinationName(r: MessageReceiveResult[IO, TibrvMsg]) =
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

  def createRequestResponseClient(f: String => IO[RequestResponseClient[IO, TibrvMsg, TibrvMsg]])(implicit context: BridgeContext) =
    delay {
      r: MessageReceiveResult[IO, TibrvMsg] =>
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

  def requestResponseClientForDestination(destination: String)(implicit context: BridgeContext) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit c =>
        createRequestResponseClient(d =>
          info[IO](s"Creating Tibrv request-response client for $d") >>
            TibrvServiceConfiguration.requestResponse[TibrvMsg, TibrvMsg](Seq(d)).client[IO]
        )
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit c =>
        createRequestResponseClient(d =>
          info[IO](s"Creating Pulsar request-response client for $d") >>
            PulsarServiceConfiguration.requestResponse[TibrvMsg, TibrvMsg](d).client[IO]
        )
      }
    else
      raiseError(new IllegalStateException(s"${destination} not supported"))

  def clientForSource(source: String)(implicit context: BridgeContext) =
    if (source.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        PulsarServiceConfiguration.source[TibrvMsg](Right(source.r)).client[IO]
      }
    else if (source.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        TibrvServiceConfiguration.source[TibrvMsg](Seq(stripUriScheme(source))).client[IO]
      }
    else
      raiseError(new IllegalStateException(s"${source} not supported"))

  def createSenderClient(f: String => IO[SenderClient[IO, TibrvMsg]]) =
    delay {
      r: MessageReceiveResult[IO, TibrvMsg] =>
        for {
          d <- getDestinationName(r)
          r <- d.map(f).sequence
        } yield r
    }

  def clientForDestination(destination: String)(implicit context: BridgeContext) =
    if (destination.startsWith(tibrvScheme))
      context.withTibrvContext { implicit context =>
        createSenderClient(d => TibrvServiceConfiguration.destination[TibrvMsg](d).client[IO])
      }
    else if (destination.startsWith(pulsarScheme))
      context.withPulsarContext { implicit context =>
        createSenderClient(d => PulsarServiceConfiguration.destination[TibrvMsg](d).client[IO])
      }
    else
      raiseError(new IllegalStateException(s"${destination} not supported"))

  def startRequestResponseBridge[REQ: Unmarshaller, RESP: Marshaller](
    source: RequestHandler[IO, REQ, RESP] => IO[Service[IO]],
    destination: MessageReceiveResult[IO, REQ] => IO[Option[RequestResponseClient[IO, REQ, RESP]]]
  )(
    implicit mapping: RequestResponseMapping[REQ, RESP]
  ) =
    for {
      bridge <- RequestResponseBridge(source, destinationBasedOnSourceRequest(destination), requestResponseOnlyFilter[IO, REQ])
      serviceState <- bridge.start
    } yield serviceState

  def startForwarderBridge[M: Marshaller : Unmarshaller](
    sourceClient: ReceiverClient[IO, M],
    destinationClient: MessageReceiveResult[IO, M] => IO[Option[SenderClient[IO, M]]]
  ) =
    for {
      bridge <- ForwarderBridge(sourceClient, destinationBasedOnSource(destinationClient), oneWayOnlyFilter[IO, M])
      serviceState <- bridge.start
    } yield serviceState
}
