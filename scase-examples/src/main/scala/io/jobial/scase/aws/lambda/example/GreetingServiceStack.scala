package io.jobial.scase.aws.lambda.example

import io.jobial.scase.aws.lambda.{IOLambdaRequestHandler, LambdaRequestResponseServiceConfiguration}
import io.jobial.scase.cloudformation.{CloudformationStackApp, StackContext}
import io.jobial.scase.marshalling.circe.{circeMarshaller, circeUnmarshaller}
import io.circe.generic.auto._
import io.jobial.scase.marshalling.circe._

object GreetingServiceLambdaRequestHandler
  extends GreetingService
    with IOLambdaRequestHandler[GreetingRequest[_], GreetingResponse] {

  val requestUnmarshaller = circeUnmarshaller[GreetingRequest[_]]

  val responseMarshaller = circeMarshaller[GreetingResponse]
}

trait GreetingServiceConfig {

  val greetingServiceConfig = LambdaRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}

object GreetingServiceStack extends CloudformationStackApp with GreetingServiceConfig {

  def template(implicit context: StackContext) =
    lambda(greetingServiceConfig, GreetingServiceLambdaRequestHandler)
}

