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

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.{ClientContext, CognitoIdentity, Context, LambdaLogger}
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.ByteArrayInputStream

class LambdaRequestHandlerTest extends RequestResponseTestSupport with TestHandlerLambdaConfig {

  "calling lambda handler" should "work" in {
    IO {
      val out = new ByteArrayOutputStream()
      
      new LambdaTestHandler().handleRequest(
        new ByteArrayInputStream(serviceConfiguration.requestMarshaller.marshal(request1)),
        out,
        new Context {
          override def getAwsRequestId: String = ""

          override def getLogGroupName: String = ???

          override def getLogStreamName: String = ???

          override def getFunctionName: String = ???

          override def getFunctionVersion: String = ???

          override def getInvokedFunctionArn: String = ???

          override def getIdentity: CognitoIdentity = ???

          override def getClientContext: ClientContext = ???

          override def getRemainingTimeInMillis: Int = ???

          override def getMemoryLimitInMB: Int = ???

          override def getLogger: LambdaLogger = ???
        }
      )
      println(out.toString("utf-8"))
      assert(serviceConfiguration.responseUnmarshaller.unmarshal(out.toByteArray) === Right(response1))
    }
  }
}

trait TestHandlerLambdaConfig {

  val serviceConfiguration = LambdaRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("test")
}

class LambdaTestHandler
  extends IOLambdaRequestHandler[TestRequest[_ <: TestResponse], TestResponse]
    with TestRequestHandler
    with TestHandlerLambdaConfig {
}
