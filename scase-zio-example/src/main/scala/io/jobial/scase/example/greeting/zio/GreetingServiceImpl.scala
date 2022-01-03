package io.jobial.scase.example.greeting.zio

import zio._
import io.jobial.scase.core._
import io.jobial.scase.example.greeting._

trait GreetingService extends RequestHandler[Task, GreetingRequest[_ <: GreetingResponse], GreetingResponse] {

  def handleRequest(implicit context: RequestContext[Task]) = {
    case m: Hello =>
      ZIO(m ! HelloResponse(s"Hello, ${m.person}!"))
    case m: Hi =>
      for {
        _ <- ZIO.effectTotal(println("processing request..."))
      } yield m ! HiResponse(s"Hi ${m.person}!") 
  }
}
