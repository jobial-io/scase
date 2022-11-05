package io.jobial.scase.marshalling.tibrv.raw

import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.marshalling.Marshalling
import io.jobial.scase.marshalling.sprayjson.DefaultFormats

class TibrvMsgRawMarshalling extends Marshalling[TibrvMsg] with io.jobial.scase.marshalling.tibrv.raw.TibrvMsgRawMarshallingInstances with DefaultFormats {

  val marshaller = tibrvMsgRawMarshaller

  val unmarshaller = tibrvMsgRawUnmarshaller

  val eitherMarshaller = tibrvMsgRawEitherMarshaller

  val eitherUnmarshaller = tibrvMsgRawEitherUnmarshaller

  val throwableMarshaller = tibrvMsgRawThrowableMarshaller

  val throwableUnmarshaller = tibrvMsgRawThrowableUnmarshaller

}
