package io.jobial.scase.marshalling.circe

import io.circe.{Decoder, Encoder}

trait CirceDefaultCodecs {

  implicit val throwableEncoder: Encoder[Throwable] = ???

  implicit val throwableDecoder: Decoder[Throwable] = ???
}
