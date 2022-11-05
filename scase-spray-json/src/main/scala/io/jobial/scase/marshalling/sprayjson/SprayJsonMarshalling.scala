package io.jobial.scase.marshalling.sprayjson

import io.jobial.scase.marshalling.Marshalling
import spray.json.JsonFormat

class SprayJsonMarshalling[M: JsonFormat] extends Marshalling[M] with io.jobial.scase.marshalling.sprayjson.SprayJsonMarshallingInstances with DefaultFormats {
  val marshaller = sprayJsonMarshaller[M]

  val unmarshaller = sprayJsonUnmarshaller[M]

  val eitherMarshaller = sprayJsonMarshaller[Either[Throwable, M]]

  val eitherUnmarshaller = sprayJsonUnmarshaller[Either[Throwable, M]]

  val throwableMarshaller = sprayJsonMarshaller[Throwable]

  val throwableUnmarshaller = sprayJsonUnmarshaller[Throwable]
}
