package io.jobial.scase.marshalling.tibrv.circe

import io.circe.generic.auto._
import io.jobial.scase.core.TestResponse1
import io.jobial.scase.marshalling.circe.DefaultCodecs
import io.jobial.scase.marshalling.tibrv.Employee
import io.jobial.scase.marshalling.tibrv.TibrvMsgMarshallingTestSupport
import io.jobial.scase.marshalling.tibrv.circe

class TibrvMsgCirceMarshallingTest extends TibrvMsgMarshallingTestSupport with TibrvMsgCirceMarshalling with DefaultCodecs {

  override implicit def employeeMarshaller = tibrvMsgCirceMarshaller[Employee]
  
  override implicit def employeeUnmarshaller = tibrvMsgCirceUnmarshaller[Employee]

  "marshalling" should "work" in {
    testMarshalling(response1, new circe.javadsl.TibrvMsgCirceMarshalling[TestResponse1] {})
  }
}
