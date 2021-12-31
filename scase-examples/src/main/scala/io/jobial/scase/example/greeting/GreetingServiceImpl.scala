package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.scase.core._

trait GreetingService extends RequestProcessor[IO, GreetingRequest[_ <: GreetingResponse], GreetingResponse] {

  def processRequest(implicit context: RequestContext[IO]) = {
    case m: Hello =>
      m ! HelloResponse(s"Hello, ${m.person}!")
    case m: Hi =>
      for {
        _ <- IO(println(s"processing request $m..."))
      } yield m ! HiResponse(s"Hi ${m.person}!") 
  }
}
