package io.jobial.scase.example.greeting.zio

import io.jobial.scase.example.greeting.{GreetingRequest, GreetingResponse}
import io.jobial.scase.local.LocalRequestResponseServiceConfiguration
import io.jobial.scase.pulsar.{PulsarContext, PulsarRequestResponseServiceConfiguration}
import spray.json._

/**
 * For the sake of convenience, the Spray JsonFormats are derived from Circe here, hence the
 * added CirceSprayJsonSupport - in a real application it makes no difference how these formats are implemented.
 * The Marshallers/Umarshallers are derived from whatever Spray JsonFormats are available.
 */
trait GreetingServiceConfig {

  val greetingServiceConfig =
    LocalRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}