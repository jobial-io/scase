package io.jobial.scase.local

import cats.effect.IO
import io.jobial.scase.core._
import scala.concurrent.duration.DurationInt

class LocalRequestResponseServiceTest
  extends RequestResponseTestSupport {

  val config = LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello")

  val requestProcessor = new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def processRequest(implicit context: RequestContext[IO]): Processor = {
      case r: TestRequest1 =>
        println("replying...")
        r.reply(response1)
    }
  }

  implicit val sendRequestContext = SendRequestContext(10.seconds)

  trait Req

  trait Resp

  case class Req1() extends Req

  case class Resp1() extends Resp

  implicit def m: RequestResponseMapping[Req1, Resp1] = ???

  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestProcessor)
      (service, client) = t
      _ <- service.startService
      r <- client.sendRequest(request1)
      r1 <- client ? request1
    } yield assert(response1 == r)
  }

  "another request-response service" should "reply successfully" in {
    val c: RequestResponseClient[IO, Req, Resp] = ???

    val x = c.sendRequest(Req1())
    val y = c ? Req1()
    ???
  }
}
