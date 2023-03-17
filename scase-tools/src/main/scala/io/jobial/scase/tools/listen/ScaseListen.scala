package io.jobial.scase.tools.listen

import cats.effect.IO
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.jms.JMSServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.rawbytes._
import io.jobial.scase.marshalling.serialization.serializationMarshalling
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.tools.bridge.ActiveMQEndpointInfo
import io.jobial.scase.tools.bridge.EndpointInfo
import io.jobial.scase.tools.bridge.EndpointInfoParser
import io.jobial.scase.tools.bridge.PulsarEndpointInfo
import io.jobial.scase.tools.bridge.TibrvEndpointInfo
import io.jobial.scase.util.tryIncludingFatal
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.SubscriptionInitialPosition.Earliest

import java.time.Instant
import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt
import scala.io.AnsiColor._
import scala.util.Try

object ScaseListen extends CommandLineApp with EndpointInfoParser with Logging {

  def run =
    for {
      source <- opt[EndpointInfo]("source", "s").required
      messageSizeLimit <- opt[Int]("message-size-limit").
        description("Truncate message above this size")
      context = ScaseListenContext(source, messageSizeLimit)
    } yield run(context)

  def run(implicit context: ScaseListenContext) =
    for {
      service <- handlerService
      _ <- service.startAndJoin
    } yield ()

  val defaultPulsarSubscriptionName = s"scase-listen-${randomUUID}"

  val defaultSubscriptionInitialPosition = Some(Earliest)

  def defaultSubscriptionInitialPublishTime = Some(Instant.now.minusSeconds(60))

  def handlerService(implicit context: ScaseListenContext) =
    context.source match {
      case source: PulsarEndpointInfo =>
        source.withPulsarContext { implicit pulsarContext =>
          PulsarServiceConfiguration.handler[Array[Byte]](
            Right(source.topicPattern),
            Some(1.second),
            source.subscriptionInitialPosition.orElse(defaultSubscriptionInitialPosition),
            source.subscriptionInitialPublishTime.orElse(defaultSubscriptionInitialPublishTime),
            source.subscriptionName.getOrElse(defaultPulsarSubscriptionName)
          ).service[IO](messageHandler)
        }
      case source: TibrvEndpointInfo =>
        source.withTibrvContext { implicit tibrvContext =>
          println(source)
          TibrvServiceConfiguration.handler[Array[Byte]](source.subjects).service[IO](messageHandler)
        }
      case source: ActiveMQEndpointInfo =>
        source.withJMSSession { implicit session =>
          JMSServiceConfiguration.handler[Array[Byte]]("", source.destination).service[IO](messageHandler)
        }
      case _ =>
        IO.raiseError(new IllegalStateException(s"${context.source} not supported"))
    }

  val tibrvUnmarshaller = tryIncludingFatal(Class.forName(s"${getClass.getPackageName}.TibrvMsgUnmarshaller")
    .getDeclaredConstructor().newInstance().asInstanceOf[Unmarshaller[String]]).toEither

  val serializationUnmarshaller = serializationMarshalling[AnyRef].unmarshaller

  def messageHandler(implicit context: ScaseListenContext) = MessageHandler[IO, Array[Byte]](implicit messageContext => { message =>
    for {
      source <- messageContext.receiveResult().sourceName.handleError(_ => "<unknown source>")
      publishTime <- messageContext.receiveResult().publishTime.map(_.toString).handleError(_ => "<no timestamp>")
      content <- IO {
        tibrvUnmarshaller.flatMap(_.unmarshal(message))
          .orElse(serializationUnmarshaller.unmarshal(message).map(_.toString))
          .orElse(Try(new String(message, "UTF-8").replaceAll("\\P{Print}", ".")).toEither)
          .getOrElse("<message could not be decoded>")
      }
      _ <- IO {
        val messageSizeLimit = context.messageSizeLimit.getOrElse(Int.MaxValue)
        println(s"${YELLOW}${publishTime} ${GREEN}${source}${RESET} ${content.take(messageSizeLimit)}${if (content.size > messageSizeLimit) "..." else ""}")
      }
    } yield ()
  })
}

case class ScaseListenContext(
  source: EndpointInfo,
  messageSizeLimit: Option[Int] = None
)