package io.jobial.scase.example.greeting

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

object GreetingServiceStack extends CloudformationStackApp with GreetingServiceConfig {

  def template(implicit context: StackContext) =
    lambda(greetingServiceConfig, GreetingServiceLambdaRequestHandler)
}

