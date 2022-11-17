package io.jobial.scase.activemq

import cats.effect.IO.delay
import cats.effect.IO.pure
import io.jobial.scase.core.test.ScaseTestHelper
import org.scalatest.flatspec.AsyncFlatSpec

class ActiveMQContextTest extends AsyncFlatSpec with ScaseTestHelper {

  "creating session" should "work" in {
    delay(ActiveMQContext().session.close) >> pure(succeed)
  }
}
