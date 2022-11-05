package io.jobial.scase.marshalling.tibrv.sprayjson

import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.core.test.TestResponse1
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.tibrv.Address
import io.jobial.scase.marshalling.tibrv.Employee
import io.jobial.scase.marshalling.tibrv.TibrvMsgMarshallingTestSupport

class TibrvMsgSprayJsonMarshallingTest extends TibrvMsgMarshallingTestSupport with TibrvMsgSprayJsonMarshallingInstances {

  implicit val addressFormat = jsonFormat1(Address)

  implicit val testPersonFormat = jsonFormat10(Employee)

  implicit val testRequest1Format = jsonFormat1(TestRequest1)

  implicit val testResponse1Format = jsonFormat2(TestResponse1)

  implicit def employeeMarshaller: Marshaller[Employee] = tibrvMsgSprayJsonMarshaller[Employee]

  implicit def employeeUnmarshaller: Unmarshaller[Employee] = tibrvMsgSprayJsonUnmarshaller[Employee]

  "marshalling" should "work" in {
    testMarshalling(response1, new TibrvMsgSprayJsonMarshalling[TestResponse1] {})
  }
}
