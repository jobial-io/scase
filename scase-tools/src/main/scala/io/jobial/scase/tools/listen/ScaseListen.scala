package io.jobial.scase.tools.listen

import cats.effect.IO
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.rawbytes._
import io.jobial.scase.marshalling.serialization.serializationMarshalling
import io.jobial.scase.tools.endpoint.Endpoint.handlerService
import io.jobial.scase.tools.endpoint.Endpoint
import io.jobial.scase.tools.endpoint.EndpointParser
import io.jobial.scase.util.tryIncludingFatal
import io.jobial.sclap.CommandLineApp

import java.util.UUID.randomUUID
import scala.io.AnsiColor._
import scala.util.Try

object ScaseListen extends CommandLineApp with EndpointParser with Logging {

  def run =
    for {
      source <- opt[Endpoint]("source", "s").required
      messageSizeLimit <- opt[Int]("message-size-limit")
        .description("Truncate message above this size")
      output <- opt[String]("output", "o")
      format <- opt[String]("format").default("text")
        .description("text or binary")
      context = ScaseListenContext(source, messageSizeLimit, output, format match {
        case "binary" =>
          Binary
        case _ =>
          Text
      })
    } yield run(context)

  def run(implicit context: ScaseListenContext) =
    for {
      service <- handlerService[IO, Array[Byte]](context.source, messageHandler)
      _ <- service.startAndJoin
    } yield ()

  val defaultPulsarSubscriptionName = s"scase-listen-${randomUUID}"

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
  source: Endpoint,
  messageSizeLimit: Option[Int] = None,
  output: Option[String],
  format: Format
)

sealed trait Format

case object Binary extends Format

case object Text extends Format