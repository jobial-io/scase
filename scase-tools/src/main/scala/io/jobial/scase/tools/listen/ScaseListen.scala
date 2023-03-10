package io.jobial.scase.tools.listen

import cats.effect.IO
import cats.instances.uuid
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.rawbytes._
import io.jobial.scase.pulsar.PulsarServiceConfiguration.handler
import io.jobial.scase.tools.bridge.EndpointInfo
import io.jobial.scase.tools.bridge.PulsarEndpointInfo
import io.jobial.scase.tools.bridge.TibrvEndpointInfo
import io.jobial.sclap.CommandLineApp
import org.apache.pulsar.client.api.Message
import java.time.Instant.ofEpochMilli
import scala.io.AnsiColor._
import scala.util.Try

object ScaseListen extends CommandLineApp {

  def run =
    for {
      source <- opt[EndpointInfo]("source", "s").required
      messageSizeLimit <- opt[Int]("message-size-limit", "s").
        description("Truncate message above this size")
      context = ScaseListenContext(source, messageSizeLimit)
      r <- run(context)
    } yield r

  def run(implicit context: ScaseListenContext) = command {
    for {
      config <- IO(handler[Array[Byte]](context.topicPattern.r, None, None, s"pulsar-listen-${uuid}"))
      service <- {
        implicit val pulsarContext = context.pulsarContext
        config.service(messageHandler)
      }
      _ <- service.startAndJoin
    } yield ()
  }
  
  def handler(implicit context: ScaseListenContext) =
    context.source match {
      case source: PulsarEndpointInfo =>
        context.withSourcePulsarContext { implicit context =>
          PulsarServiceConfiguration.source[M](
            Right(source.topicPattern),
            Some(1.second),
            source.subscriptionInitialPosition.orElse(defaultSubscriptionInitialPosition),
            source.subscriptionInitialPublishTime.orElse(defaultSubscriptionInitialPublishTime),
            source.subscriptionName.getOrElse(defaultPulsarSubscriptionName)
          ).client[IO]
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


  val tibrvUnmarshaller = Try(Class.forName("io.jobial.pulsar.tools.listen.TibrvMsgUnmarshaller")
    .getDeclaredConstructor().newInstance().asInstanceOf[Unmarshaller[String]]).toEither

  def messageHandler(implicit context: ScaseListenContext) = MessageHandler[IO, Array[Byte]](implicit messageContext => { message =>
    for {
      pulsarMessage <- messageContext.receiveResult().underlyingMessage[Message[_]]
      _ <- IO {
        val result = tibrvUnmarshaller.flatMap(_.unmarshal(message))
          .getOrElse(Try(new String(message, "UTF-8").replaceAll("\\P{Print}", ".")).toEither).toString
        val messageSizeLimit = context.messageSizeLimit.getOrElse(Int.MaxValue)
        println(s"${YELLOW}${ofEpochMilli(pulsarMessage.getPublishTime)} ${GREEN}${pulsarMessage.getTopicName}${RESET} ${result.take(messageSizeLimit)}${if (result.size > messageSizeLimit) "..." else ""}")
      }
    } yield ()
  })
}

case class ScaseListenContext(
  source: EndpointInfo,
  messageSizeLimit: Option[Int] = None
) {
}