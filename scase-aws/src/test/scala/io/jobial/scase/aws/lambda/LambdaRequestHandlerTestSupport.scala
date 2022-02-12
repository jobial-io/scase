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

import cats.Eq
import cats.effect.IO
import com.amazonaws.services.lambda.runtime.{ClientContext, CognitoIdentity, Context, LambdaLogger, RequestStreamHandler}
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import org.apache.commons.io.output.ByteArrayOutputStream
import cats.implicits.catsSyntaxParallelSequence_

import java.io.ByteArrayInputStream
import cats.implicits._

trait LambdaRequestHandlerTestSupport extends RequestResponseTestSupport {

  val emptyContext = new Context {
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
  
  def testLambdaHandler[F[_], REQ, RESP: Eq](handler: LambdaRequestHandler[F, REQ, RESP], request: REQ, response: RESP) =
    IO {
      val out = new ByteArrayOutputStream()

      handler.handleRequest(
        new ByteArrayInputStream(handler.serviceConfiguration.requestMarshaller.marshal(request)),
        out,
        emptyContext
      )
      println(out.toString("utf-8"))
      assert(handler.serviceConfiguration.responseUnmarshaller.unmarshal(out.toByteArray) === response.asRight[Throwable])
    }

}
