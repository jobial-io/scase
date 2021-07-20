package io.jobial.scase.example.greeting

import io.jobial.scase.aws.lambda.LambdaRequestResponseServiceConfiguration
import io.jobial.scase.core._
import io.circe.generic.auto._
import io.jobial.scase.marshalling.circe._


sealed trait GreetingRequest[RESPONSE] extends Request[RESPONSE]

sealed trait GreetingResponse

case class Hello(person: String) extends GreetingRequest[HelloResponse]

case class HelloResponse(sayingHello: String) extends GreetingResponse

case class Hi(person: String) extends GreetingRequest[HiResponse]

case class HiResponse(sayingHi: String) extends GreetingResponse

trait GreetingServiceConfig {

  val greetingServiceConfig = LambdaRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}



