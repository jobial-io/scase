package io.jobial.scase.pulsar

import cats.effect.IO
import io.jobial.scase.core.{RequestContext, RequestProcessor, RequestResponseMapping, RequestResponseTestSupport, SendRequestContext, TestRequest, TestRequest1, TestRequest2, TestResponse}
import io.jobial.scase.local.{LocalRequestResponseServiceConfiguration, localServiceAndClient}
import concurrent.duration._
import scala.concurrent.TimeoutException

class PulsarRequestResponseServiceTest
  extends RequestResponseTestSupport {

  val requestProcessor = new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def processRequest(implicit context: RequestContext[IO]) = {
      case r: TestRequest1 =>
        println("replying...")
        r ! response1
      case r: TestRequest2 =>
        println("replying...")
        r ! response2
    }
  }

  trait Req

  trait Resp

  case class Req1() extends Req

  case class Resp1() extends Resp

  implicit def m = new RequestResponseMapping[Req1, Resp1] {}

  val anotherRequestProcessor = new RequestProcessor[IO, Req, Resp] {
    override def processRequest(implicit context: RequestContext[IO]): Processor = {
      case r: Req1 =>
        println("replying...")
        r.reply(Resp1())
    }
  }

  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestProcessor)
      (service, client) = t
      _ <- service.startService
      r1 <- client.sendRequest(request1)
      r11 <- client ? request1
      r2 <- client.sendRequest(request2)
      r21 <- client ? request2
    } yield assert(
      response1 === r1 && response1 === r11 &&
        response2 === r2 && response2 === r21
    )
  }

  "another request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[Req, Resp]("hello").serviceAndClient(anotherRequestProcessor)
      (service, client) = t
      _ <- service.startService
      r <- client.sendRequest(Req1())
      r1 <- client ? Req1()
    } yield assert(Resp1() == r)
  }

  "request" should "time out if service is not started" in {
    implicit val context = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[TimeoutException] {
      for {
        t <- localServiceAndClient("greeting", requestProcessor)
        (_, client) = t
        _ <- client ? request1
      } yield succeed
    }
  }
}