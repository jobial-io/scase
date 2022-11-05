package io.jobial.scase.marshalling.tibrv

import spray.json.JsonFormat

package object sprayjson extends TibrvMsgSprayJsonMarshallingInstances {

  implicit def tibrvMsgSprayJsonMarshalling[T: JsonFormat] = new TibrvMsgSprayJsonMarshalling[T]
}
