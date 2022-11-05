package io.jobial.scase.marshalling.tibrv.raw

import cats.instances.all._
import cats.kernel.Eq
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.marshalling.MarshallingTestSupport

class TibrvMsgRawMarshallingTest extends MarshallingTestSupport with TibrvMsgRawMarshallingInstances {

  implicit val tibrvMsgEq = Eq.by[TibrvMsg, String](_.toString)

  "marshalling" should "work" in {
    val message = new TibrvMsg
    message.add("name", "X")
    testMarshalling(message, new TibrvMsgRawMarshalling {})
  }

}
