package io.jobial.scase.marshalling.circe

import io.circe.generic.auto._
import io.jobial.scase.marshalling.MarshallingTestSupport

class CirceMarshallingTest extends MarshallingTestSupport {
  
  "marshalling" should "work" in {
    testMarshallingWithDefaultFormats(response1)
  }
}
