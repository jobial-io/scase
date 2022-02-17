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
package io.jobial.scase.aws.sqs

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.util.Hash.uuid


class SqsRequestResponseServiceTest
  extends RequestResponseTestSupport {
  
  "request-response service" should "reply successfully" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[Req, Resp](s"test-another-${uuid(5)}")

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-timeout-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testTimeout(client)
    } yield r
  }

  "request-response service" should "reply with error" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-error-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      client <- serviceConfig.client[IO]
      r <- testErrorReply(service, client)
    } yield r
  }

}
