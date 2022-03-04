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
package io.jobial.scase.local

import io.jobial.scase.core._

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt


class LocalRequestResponseServiceTest
  extends RequestResponseTestSupport {

  "request-response service" should "reply successfully" in {
    for {
      (service, client) <- LocalServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestHandler)
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    for {
      (service, client) <- LocalServiceConfiguration[Req, Resp]("hello").serviceAndClient(anotherRequestProcessor)
      _ <- service.start
      r <- testSuccessfulReply(client, Req1(), Resp1())
    } yield r
  }

  "request" should "time out if service is not started" in {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[TimeoutException] {
      for {
        t <- localServiceAndClient("greeting", requestHandler)
        (_, client) = t
        _ <- client ? request1
      } yield succeed
    }
  }
}
