package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.scase.core._

sealed trait GreetingRequest[RESPONSE] extends Request[RESPONSE]

sealed trait GreetingResponse

case class Hello(person: String) extends GreetingRequest[HelloResponse]

case class HelloResponse(sayingHello: String) extends GreetingResponse

case class Hi(person: String) extends GreetingRequest[HiResponse]

case class HiResponse(sayingHi: String) extends GreetingResponse

trait GreetingService extends RequestProcessor[IO, GreetingRequest[_], GreetingResponse] {

  override def processRequest(implicit context: RequestContext[IO]) = {
    case m: Hello =>
      m.reply(HelloResponse(s"Hello, ${m.person}!"))
    case m: Hi =>
      m.reply(HiResponse(s"Hi ${m.person}!"))
  }
}



