package io.jobial.scase.example.greeting.pulsar

import io.jobial.scase.core._
import io.jobial.scase.local.localServiceAndClient
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.TimeoutException
import scala.concurrent.duration._


class GreetingServiceTest
  extends AsyncFlatSpec
    with ScaseTestHelper
    with GreetingServiceSqsConfig {

  "request-response service" should "reply successfully" in {
    for {
      t <- localServiceAndClient("greeting", new GreetingService {})
      (service, client) = t
      _ <- service.start
      helloResponse <- client ? Hello("everyone")
      hiResponse <- client ? Hi("everyone")
    } yield {
      assert(helloResponse.sayingHello === "Hello, everyone!")
      assert(hiResponse.sayingHi === "Hi everyone!")
    }
  }

  "request" should "time out if service is not started" in {
    implicit val context = SendRequestContext(requestTimeout = Some(1.second))
    
    recoverToSucceededIf[TimeoutException] {
      for {
        t <- localServiceAndClient("greeting", new GreetingService {})
        (service, client) = t
        helloResponse <- client ? Hello("everyone")
      } yield succeed
    }
  }
}
