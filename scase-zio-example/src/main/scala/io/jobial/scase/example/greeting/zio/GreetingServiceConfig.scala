package io.jobial.scase.example.greeting.zio

import io.jobial.scase.local.LocalRequestResponseServiceConfiguration

trait GreetingServiceConfig {

  val greetingServiceConfig =
    LocalRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}