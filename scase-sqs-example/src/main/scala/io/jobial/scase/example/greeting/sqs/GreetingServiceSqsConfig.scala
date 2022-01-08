package io.jobial.scase.example.greeting.sqs

import io.circe.generic.auto._
import io.jobial.scase.aws.sqs.SqsRequestResponseServiceConfiguration
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.pulsar.{PulsarContext, PulsarRequestResponseServiceConfiguration}

trait GreetingServiceSqsConfig {


  val greetingServiceConfig =
    SqsRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}