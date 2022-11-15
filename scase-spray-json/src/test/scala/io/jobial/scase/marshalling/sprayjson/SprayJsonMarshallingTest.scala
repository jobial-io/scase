package io.jobial.scase.marshalling.sprayjson

import io.circe.generic.auto._
import io.jobial.scase.marshalling.MarshallingTestSupport

class SprayJsonMarshallingTest extends MarshallingTestSupport with SprayJsonMarshallingInstances with CirceSprayJsonSupport with DefaultFormats {

  "marshalling" should "work" in {
    testMarshallingWithDefaultFormats(response1)
  }
}
