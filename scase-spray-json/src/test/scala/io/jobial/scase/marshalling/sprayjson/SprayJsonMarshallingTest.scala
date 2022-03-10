package io.jobial.scase.marshalling.sprayjson

import io.circe.generic.auto._
import io.jobial.scase.marshalling.MarshallingTestSupport

class SprayJsonMarshallingTest extends MarshallingTestSupport with CirceSprayJsonSupport {

  "marshalling" should "work" in {
    testMarshallingWithDefaultFormats(response1)
  }
}
