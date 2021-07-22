package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.scase.core._
import io.jobial.scase.local.{LocalRequestResponseServiceConfiguration, localServiceAndClient}
import org.scalatest.flatspec.AsyncFlatSpec

class GreetingServiceTest
  extends AsyncFlatSpec
    with ScaseTestHelper
    with GreetingServiceConfig {

  "request-response service" should "reply successfully" in {
    for {
      t <- localServiceAndClient("greeting", new GreetingService {})
      (service, client) = t
      _ <- service.startService
      helloResponse <- client ? Hello("everyone")
      hiResponse <- client ? Hi("everyone")
    } yield {
      assert(helloResponse.sayingHello === "Hello, everyone!")
      assert(hiResponse.sayingHi === "Hi everyone!")
    }
  }

}
