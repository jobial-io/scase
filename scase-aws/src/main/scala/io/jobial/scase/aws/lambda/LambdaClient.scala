package io.jobial.scase.aws.lambda

import com.amazonaws.services.lambda.model.{InvokeRequest, InvokeResult}
import com.amazonaws.services.lambda.{AWSLambdaAsync, AWSLambdaAsyncClientBuilder}
import io.jobial.scase.aws.util.AwsClient

import scala.concurrent.{ExecutionContext, Future}


trait LambdaClient extends AwsClient {
  lazy val lambda = buildAwsAsyncClient[AWSLambdaAsyncClientBuilder, AWSLambdaAsync](AWSLambdaAsyncClientBuilder.standard)

  def invoke(functionName: String, payload: String)(implicit ec: ExecutionContext): Future[InvokeResult] = for {
    result <- lambda.invokeAsync(new InvokeRequest()
      .withFunctionName(functionName)
      .withPayload(payload)
    )
//    result <-
//      // TODO: review if this is correct...
//      if (Option(result.getFunctionError).isDefined)
//        failed(unmarshalException(result))
//      else
//        successful(result)
  } yield result

//  private def unmarshalException(result: InvokeResult) = {
//    val responseString = new String(result.getPayload.array, "utf-8")
//    println(responseString)
//    val o = responseString.parseJson.asJsObject
//    val errorType = o.fields("errorType").asInstanceOf[JsString].value
//    val errorMessage = o.fields("errorMessage").asInstanceOf[JsString].value
//    Class.forName(errorType).getConstructor(classOf[String]).newInstance(errorMessage).asInstanceOf[Throwable]
//    // TODO: unmarshal stacktrace, etc...
//    // TODO: handle non-java errors...
//  }
//
}
