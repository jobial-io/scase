package io.jobial.scase.aws.lambda.example

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.aws.lambda.IOLambdaRequestHandler
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.cloudformation.{CloudformationStack, StackContext}
import io.jobial.scase.core.{Request, RequestContext, RequestProcessor}
import io.jobial.scase.marshalling.circe._

sealed trait HelloExampleRequest[RESPONSE] extends Request[RESPONSE]

sealed trait HelloExampleResponse

case class Hello(message: String) extends HelloExampleRequest[HelloResponse]

case class HelloResponse(responseMessage: String) extends HelloExampleResponse

trait HelloExample extends RequestProcessor[IO, HelloExampleRequest[_], HelloExampleResponse] {

  override def processRequest(implicit context: RequestContext[IO]): Processor = {
    case m: Hello =>
      m.reply(HelloResponse(s"received: ${m.message}"))
  }
}

object HelloExampleLambdaRequestHandler
  extends HelloExample
    with IOLambdaRequestHandler[HelloExampleRequest[_], HelloExampleResponse] {

  val requestUnmarshaller = circeUnmarshaller[HelloExampleRequest[_]]

  val responseMarshaller = circeMarshaller[HelloExampleResponse]
}

object HelloExampleStack extends CloudformationStack {
  
  def template(implicit context: StackContext) =
    lambda(HelloExampleLambdaRequestHandler)

  override def defaultAccountId: String = ???

  override def defaultContainerImageRootUrl: String = ???

  override def defaultLambdaCodeS3Path: String = ???

  override def awsContext: AwsContext = ???
}