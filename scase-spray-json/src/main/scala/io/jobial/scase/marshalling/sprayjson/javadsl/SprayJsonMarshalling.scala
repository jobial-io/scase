package io.jobial.scase.marshalling.sprayjson.javadsl

import io.jobial.scase.marshalling.javadsl.Marshalling
import io.jobial.scase.marshalling.sprayjson.DefaultFormats
import spray.json.JsonFormat

class SprayJsonMarshalling[M: JsonFormat] extends Marshalling[M] with io.jobial.scase.marshalling.sprayjson.SprayJsonMarshalling with DefaultFormats {
  val marshaller = sprayJsonMarshaller[M]

  val unmarshaller = sprayJsonUnmarshaller[M]

  val eitherMarshaller = sprayJsonMarshaller[Either[Throwable, M]]

  val eitherUnmarshaller = sprayJsonUnmarshaller[Either[Throwable, M]]

  val throwableMarshaller = sprayJsonMarshaller[Throwable]

  val throwableUnmarshaller = sprayJsonUnmarshaller[Throwable]
}
