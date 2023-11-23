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
import cats.implicits._
import com.amazonaws.services.lambda.model.GetFunctionRequest
import com.amazonaws.services.lambda.model.GetFunctionResult
import com.amazonaws.services.lambda.model.InvokeRequest
import com.amazonaws.services.lambda.model.ListFunctionsRequest

import scala.collection.JavaConverters._

trait LambdaClient[F[_]] extends AwsClient[F] {

  def invoke(functionName: String, payload: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      result <- fromJavaFuture(context.lambda.invokeAsync(new InvokeRequest()
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
  //    trace(responseString)
  //    val o = responseString.parseJson.asJsObject
  //    val errorType = o.fields("errorType").asInstanceOf[JsString].value
  //    val errorMessage = o.fields("errorMessage").asInstanceOf[JsString].value
  //    Class.forName(errorType).getConstructor(classOf[String]).newInstance(errorMessage).asInstanceOf[Throwable]
  //    // TODO: unmarshal stacktrace, etc...
  //    // TODO: handle non-java errors...
  //  }
  //
  
  def listFunctions(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.lambda.listFunctionsAsync(new ListFunctionsRequest()))
      .map(_.getFunctions.asScala.toList)

  def getFunction(functionName: String)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.lambda.getFunctionAsync(new GetFunctionRequest().withFunctionName(functionName)))

  implicit val getFunctionResultTagged = new Tagged[GetFunctionResult] {
    def tags(tagged: GetFunctionResult) = tagged.getTags.asScala.toList.map(t => Tag(t._1, t._2))
  }

}
