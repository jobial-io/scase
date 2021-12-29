package io.jobial.scase.marshalling.circe

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

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
        klass <- e.downField("type").as[String]
      } yield
        Class.forName(klass).getConstructor(classOf[String]).newInstance(message).asInstanceOf[Throwable]
    }
  }
}
