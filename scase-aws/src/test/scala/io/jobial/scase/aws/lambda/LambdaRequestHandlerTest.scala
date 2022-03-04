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
package io.jobial.scase.aws.lambda

import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._

class LambdaRequestHandlerTest extends LambdaRequestHandlerTestSupport {

  "calling lambda handler" should "work" in {
    testLambdaHandler(new LambdaTestHandler(), request1, response1)
  }
}

trait TestHandlerLambdaConfig {

  val serviceConfiguration = LambdaServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("test")
}

class LambdaTestHandler
  extends IOLambdaRequestHandler[TestRequest[_ <: TestResponse], TestResponse]
    with TestRequestHandler
    with TestHandlerLambdaConfig
