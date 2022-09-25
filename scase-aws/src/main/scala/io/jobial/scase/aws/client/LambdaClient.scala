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
package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.effect.Timer
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder
import com.amazonaws.services.lambda.model.InvokeRequest
import cats.implicits._

trait LambdaClient[F[_]] extends AwsClient[F] {
  lazy val lambda = buildAwsAsyncClient[AWSLambdaAsyncClientBuilder, AWSLambdaAsync](AWSLambdaAsyncClientBuilder.standard)

  def invoke(functionName: String, payload: String) =
    for {
      result <- fromJavaFuture(lambda.invokeAsync(new InvokeRequest()
        .withFunctionName(functionName)
        .withPayload(payload)))
      //    result <-
      //      // TODO: review if this is correct...
      //      if (Option(result.getFunctionError).isDefined)
      //        failed(unmarshalException(result))
      //      else
      //        successful(result)
    } yield result



  //  private def unmarshalException(result: InvokeResult) = {
  //    val responseString = new String(result.getPayload.array, "utf-8")
  //    debug(responseString)
  //    val o = responseString.parseJson.asJsObject
  //    val errorType = o.fields("errorType").asInstanceOf[JsString].value
  //    val errorMessage = o.fields("errorMessage").asInstanceOf[JsString].value
  //    Class.forName(errorType).getConstructor(classOf[String]).newInstance(errorMessage).asInstanceOf[Throwable]
  //    // TODO: unmarshal stacktrace, etc...
  //    // TODO: handle non-java errors...
  //  }
  //
}

object LambdaClient {

  def apply[F[_] : Concurrent : Timer](implicit context: AwsContext) =
    new LambdaClient[F] {
      def awsContext = context

      val concurrent = Concurrent[F]

      val timer = Timer[F]
    }
}