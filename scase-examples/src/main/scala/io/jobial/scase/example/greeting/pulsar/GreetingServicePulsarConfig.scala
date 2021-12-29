package io.jobial.scase.example.greeting.pulsar

import io.circe.generic.auto._
import io.jobial.scase.aws.lambda.LambdaRequestResponseServiceConfiguration
import io.jobial.scase.example.greeting.{GreetingRequest, GreetingResponse}
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.pulsar.{PulsarContext, PulsarRequestResponseServiceConfiguration}

trait GreetingServicePulsarConfig {

  implicit val context = PulsarContext()

  val greetingServiceConfig =
    PulsarRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting", "greeting")
}