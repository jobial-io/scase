package io.jobial.scase.example.greeting.sprayjson


import io.circe.generic.auto._
import io.jobial.scase.example.greeting.{GreetingRequest, GreetingResponse}
import io.jobial.scase.marshalling.sprayjson._
import io.jobial.scase.pulsar.{PulsarContext, PulsarRequestResponseServiceConfiguration}
import spray.json._

/**
 * For the sake of convenience, the Spray JsonFormats are derived from Circe here, hence the
 * added CirceSprayJsonSupport - in a real application it makes no difference how these formats are implemented.
 * The Marshallers/Umarshallers are derived from whatever Spray JsonFormats are available.
 */
trait GreetingServicePulsarConfig extends DefaultJsonProtocol with CirceSprayJsonSupport {

  implicit val context = PulsarContext()

  val greetingServiceConfig =
    PulsarRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}