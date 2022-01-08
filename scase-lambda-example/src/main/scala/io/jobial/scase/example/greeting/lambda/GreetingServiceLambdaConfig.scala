package io.jobial.scase.example.greeting.lambda

import io.jobial.scase.aws.lambda.LambdaRequestResponseServiceConfiguration
import io.circe.generic.auto._
import io.jobial.scase.marshalling.circe._

trait GreetingServiceLambdaConfig {

  val greetingServiceConfig = LambdaRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}