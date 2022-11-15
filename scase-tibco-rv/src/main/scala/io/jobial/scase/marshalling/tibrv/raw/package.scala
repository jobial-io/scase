package io.jobial.scase.marshalling.tibrv

import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.core.RequestResponseMapping

package object raw extends TibrvMsgRawMarshallingInstances {

  implicit val tibrvMsgRequestResponseMapping = new RequestResponseMapping[TibrvMsg, TibrvMsg] {}

  implicit val tibrvMsgRawMarshalling = new TibrvMsgRawMarshalling
}
