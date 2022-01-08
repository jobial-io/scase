package io.jobial.scase.example.greeting.lambda

import io.jobial.scase.core._

sealed trait GreetingRequest[RESPONSE] extends Request[RESPONSE]

sealed trait GreetingResponse

case class Hello(person: String) extends GreetingRequest[HelloResponse]

case class HelloResponse(sayingHello: String) extends GreetingResponse

case class Hi(person: String) extends GreetingRequest[HiResponse]

case class HiResponse(sayingHi: String) extends GreetingResponse





