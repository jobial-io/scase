package io.jobial.scase.marshalling.rawbytes

import cats.Eq
import io.jobial.scase.marshalling.MarshallingTestSupport
import java.util.Arrays

class RawBytesMarshallingTest extends MarshallingTestSupport {

  implicit val byteArrayEq = new Eq[Array[Byte]] {
    def eqv(x: Array[Byte], y: Array[Byte]) =
      Arrays.equals(x, y)
  }

  "marshalling" should "work" in {
    testMarshalling(Array[Byte](1, 2, 3), false)
  }
}
