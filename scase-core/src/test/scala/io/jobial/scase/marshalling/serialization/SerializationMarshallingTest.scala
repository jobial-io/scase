package io.jobial.scase.marshalling.serialization

import io.jobial.scase.marshalling.MarshallingTestSupport

class SerializationMarshallingTest extends MarshallingTestSupport {

  "marshalling" should "work" in {
    testMarshallingWithDefaultFormats(response1)
  }
}
