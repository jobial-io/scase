package io.jobial.scase.marshalling.sprayjson

import io.jobial.scase.marshalling.MarshallingTestSupport
import io.circe.generic.auto._

class SprayJsonMarshallingTest extends MarshallingTestSupport with CirceSprayJsonSupport {
  
  "marshalling" should "work" in {
    testMarshalling(response1)
  }
}
