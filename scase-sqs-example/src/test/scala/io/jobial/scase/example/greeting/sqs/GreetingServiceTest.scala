/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.example.greeting.sqs

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
