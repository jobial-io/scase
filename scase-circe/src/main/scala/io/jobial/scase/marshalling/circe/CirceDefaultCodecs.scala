package io.jobial.scase.marshalling.circe

import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}

import scala.util.Try

trait CirceDefaultCodecs {

  implicit val throwableEncoder = new Encoder[Throwable] {
    override def apply(a: Throwable): Json = Json.obj(
      "error" -> Json.obj(
        "message" -> Json.fromString(a.getMessage),
        "type" -> Json.fromString(a.getClass.getName)
      )
    )
  }

  implicit val throwableDecoder = new Decoder[Throwable] {
    override def apply(c: HCursor): Result[Throwable] = {
      val e = c.downField("error")
      for {
        message <- e.downField("message").as[String]
        className <- e.downField("type").as[String]
      } yield {
        val c = Class.forName(className)
        Try(c.getConstructor(classOf[String]).newInstance(message).asInstanceOf[Throwable]) orElse
          Try(c.newInstance.asInstanceOf[Throwable]) getOrElse
          new IllegalStateException(message)
      }
    }
  }

  // TODO: revisit this. Added encoder/decoder for Either to prevent auto generation. See https://github.com/circe/circe/issues/751 
  
  implicit def encodeEither[A, B](implicit
    encoderA: Encoder[A],
    encoderB: Encoder[B]
  ): Encoder[Either[A, B]] = {
    case Left(a) =>
      encoderA(a)
    case Right(b) =>
      encoderB(b)
  }

  implicit def decodeEither[A, B](implicit
    decoderA: Decoder[A],
    decoderB: Decoder[B]
  ): Decoder[Either[A, B]] = {
    val left: Decoder[Either[A, B]] = decoderA.map(Left.apply)
    val right: Decoder[Either[A, B]] = decoderB.map(Right.apply)
    // Prioritising right to left
    right or left
  }

}
