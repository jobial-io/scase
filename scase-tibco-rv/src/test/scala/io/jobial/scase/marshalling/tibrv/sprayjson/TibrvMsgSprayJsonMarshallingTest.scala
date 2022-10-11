package io.jobial.scase.marshalling.tibrv.sprayjson

import io.jobial.scase.core.TestRequest1
import io.jobial.scase.core.TestResponse1
import io.jobial.scase.marshalling.tibrv.Address
import io.jobial.scase.marshalling.tibrv.Employee
import io.jobial.scase.marshalling.tibrv.TibrvMsgMarshallingTestSupport

class TibrvMsgSprayJsonMarshallingTest extends TibrvMsgMarshallingTestSupport with TibrvMsgSprayJsonMarshalling {

  implicit val addressFormat = jsonFormat1(Address)

  implicit val testPersonFormat = jsonFormat10(Employee)

  implicit val testRequest1Format = jsonFormat1(TestRequest1)

  implicit val testResponse1Format = jsonFormat2(TestResponse1)

  implicit def employeeMarshaller = tibrvMsgSprayJsonMarshaller[Employee]

  implicit def employeeUnmarshaller = tibrvMsgSprayJsonUnmarshaller[Employee]

  "marshalling" should "work" in {
    testMarshalling(response1, new javadsl.TibrvMsgSprayJsonMarshalling[TestResponse1] {})
  }
}

