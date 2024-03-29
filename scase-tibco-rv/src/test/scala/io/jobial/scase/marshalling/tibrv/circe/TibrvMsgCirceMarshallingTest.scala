package io.jobial.scase.marshalling.tibrv.circe

import io.circe.generic.auto._
import io.jobial.scase.core.test.TestResponse1
import io.jobial.scase.marshalling.circe.DefaultCodecs
import io.jobial.scase.marshalling.tibrv.Employee
import io.jobial.scase.marshalling.tibrv.TibrvMsgMarshallingTestSupport

class TibrvMsgCirceMarshallingTest extends TibrvMsgMarshallingTestSupport with TibrvMsgCirceMarshallingInstances with DefaultCodecs {

  implicit def employeeMarshaller = tibrvMsgCirceMarshaller[Employee]

  implicit def employeeUnmarshaller = tibrvMsgCirceUnmarshaller[Employee]

  "marshalling" should "work" in {
    testMarshalling(response1, new TibrvMsgCirceMarshalling[TestResponse1] {})
  }
}
