package io.jobial.scase.marshalling.circe

import io.jobial.scase.marshalling.MarshallingTestSupport
import io.circe.generic.auto._

class CirceMarshallingTest extends MarshallingTestSupport {
  
  "marshalling" should "work" in {
    testMarshallingWithDefaultFormats(response1)
  }
}
