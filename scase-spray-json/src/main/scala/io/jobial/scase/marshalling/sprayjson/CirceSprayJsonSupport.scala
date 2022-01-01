package io.jobial.scase.marshalling.sprayjson

import io.circe.{Decoder, Encoder, HCursor, parser}
import spray.json._

/**
 * Compatibility layer that derives Spray JsonFormat classes from existing Circe Encoders/Decoders. 
 */
trait CirceSprayJsonSupport {

  implicit def jsonWriterFromCirceEncoder[T: Encoder] = new JsonWriter[T] {
    def write(obj: T): JsValue =
    // This is obviously awfully inefficient, should only be used for migration or backcompat purposes...
      Encoder[T].apply(obj).toString.parseJson
  }

  implicit def jsonReaderFromCirceDecoder[T: Decoder] = new JsonReader[T] {
    def read(json: JsValue): T =
    // This is obviously awfully inefficient, should only be used for migration or backcompat purposes...
      Decoder[T].apply(HCursor.fromJson(parser.parse(json.compactPrint).right.get)).right.get
  }


}
