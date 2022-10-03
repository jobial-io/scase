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

import cats.effect.IO
import io.jobial.scase.core._
import io.jobial.scase.core.impl.CatsUtils
import scala.concurrent.duration.DurationInt


class LocalRequestResponseServiceTest
  extends ServiceTestSupport with CatsUtils {

  "request-response service" should "reply successfully" in {
    for {
      (service, client) <- LocalServiceConfiguration("hello").serviceAndClient(requestHandler)
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    for {
      (service, client) <- LocalServiceConfiguration("hello").serviceAndClient(anotherRequestProcessor)
      _ <- service.start
      r <- testSuccessfulReply(client, Req1(), Resp1())
    } yield r
  }

  "request" should "time out if service is not started" in {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[RequestTimeout] {
      for {
        (_, client) <- localServiceAndClient("greeting", requestHandler)
        _ <- client ? request1
      } yield succeed
    }
  }

  "request-response service" should "succeed in load test" in {
    val serviceConfig = LocalServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello")
    for {
      service <- serviceConfig.service(requestHandler)
      h <- service.start
      client <- serviceConfig.client(service)
      r <- {
        for {
          i <- 0 until 100
        } yield for {
          _ <- debug[IO](s"sending $i")
          r <- testSuccessfulReply(client, request1, response1)
          _ <- debug[IO](s"received $i")
        } yield r
      }.parSequence
      _ <- h.stop
    } yield r
  }

  "request-response service" should "succeed in load test with different clients" in {
    val serviceConfig = LocalServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello")
    for {
      service <- serviceConfig.service(requestHandler)
      h <- service.start
      r <- {
        for {
          i <- 0 until 10
        } yield for {
          client <- serviceConfig.client(service)
          _ <- debug[IO](s"sending $i")
          r <- testSuccessfulReply(client, request1, response1)
          _ <- debug[IO](s"received $i")
        } yield r
      }.parSequence
      _ <- h.stop
    } yield r
  }

}
