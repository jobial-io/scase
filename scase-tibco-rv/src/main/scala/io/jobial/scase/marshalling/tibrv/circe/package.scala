package io.jobial.scase.marshalling.tibrv

import io.circe.Decoder
import io.circe.Encoder
import io.jobial.scase.marshalling.circe.DefaultCodecs

package object circe extends TibrvMsgCirceMarshallingInstances with DefaultCodecs {

  implicit def tibrvMsgCirceMarshalling[T: Encoder : Decoder] = new TibrvMsgCirceMarshalling[T]
}
