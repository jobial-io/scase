package io.jobial.scase.marshalling.tibrv.circe

import io.circe.Decoder
import io.circe.Encoder
import io.jobial.scase.marshalling.Marshalling
import io.jobial.scase.marshalling.circe.DefaultCodecs

class TibrvMsgCirceMarshalling[M: Encoder : Decoder] extends Marshalling[M] with io.jobial.scase.marshalling.tibrv.circe.TibrvMsgCirceMarshallingInstances with DefaultCodecs {
  val marshaller = tibrvMsgCirceMarshaller[M]

  val unmarshaller = tibrvMsgCirceUnmarshaller[M]

  val eitherMarshaller = tibrvMsgCirceMarshaller[Either[Throwable, M]]

  val eitherUnmarshaller = tibrvMsgCirceUnmarshaller[Either[Throwable, M]]

  val throwableMarshaller = tibrvMsgCirceMarshaller[Throwable]

  val throwableUnmarshaller = tibrvMsgCirceUnmarshaller[Throwable]
}
