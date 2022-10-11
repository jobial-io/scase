package io.jobial.scase.marshalling.tibrv.sprayjson.javadsl

import io.jobial.scase.marshalling.javadsl.Marshalling
import io.jobial.scase.marshalling.sprayjson.DefaultFormats
import spray.json.JsonFormat

class TibrvMsgSprayJsonMarshalling[M: JsonFormat] extends Marshalling[M] with io.jobial.scase.marshalling.tibrv.sprayjson.TibrvMsgSprayJsonMarshalling with DefaultFormats {
  val marshaller = tibrvMsgSprayJsonMarshaller[M]

  val unmarshaller = tibrvMsgSprayJsonUnmarshaller[M]

  val eitherMarshaller = tibrvMsgSprayJsonMarshaller[Either[Throwable, M]]

  val eitherUnmarshaller = tibrvMsgSprayJsonUnmarshaller[Either[Throwable, M]]

  val throwableMarshaller = tibrvMsgSprayJsonMarshaller[Throwable]

  val throwableUnmarshaller = tibrvMsgSprayJsonUnmarshaller[Throwable]
}
