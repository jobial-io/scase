package io.jobial.scase.aws.sqs

import cats.effect.IO
import io.jobial.scase.core._
import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.aws.client.Hash.uuid
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._


class SqsRequestResponseServiceTest
  extends RequestResponseTestSupport {

  val requestHandler = new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def handleRequest(implicit context: RequestContext[IO]) = {
      case r: TestRequest1 =>
        println("replying...")
        r ! response1
      case r: TestRequest2 =>
        println("replying...")
        r ! response2
    }
  }

  sealed trait Req

  sealed trait Resp

  case class Req1() extends Req

  case class Resp1() extends Resp

  implicit def m = new RequestResponseMapping[Req1, Resp1] {}
  
  implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(30.seconds))

  val anotherRequestProcessor = new RequestHandler[IO, Req, Resp] {
    override def handleRequest(implicit context: RequestContext[IO]): Handler = {
      case r: Req1 =>
        println("replying...")
        r.reply(Resp1())
    }
  }

  val requestHandlerWithError = new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def handleRequest(implicit context: RequestContext[IO]) = {
      case r: TestRequest1 =>
        println("replying...")
        r ! response1
      case r: TestRequest2 =>
        IO.raiseError(TestException("exception!!!"))
    }
  }

  "request-response service" should "reply successfully" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandler)
      _ <- service.start
      client <- serviceConfig.client[IO]
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
    val serviceConfig = SqsRequestResponseServiceConfiguration[Req, Resp](s"test-another-${uuid(5)}")

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      _ <- service.start
      client <- serviceConfig.client[IO]
      r <- client.sendRequest(Req1())
      r1 <- client ? Req1()
    } yield assert(Resp1() == r)
  }

  "request" should "time out if service is not started" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-timeout-${uuid(5)}")
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[TimeoutException] {
      for {
        service <- serviceConfig.service(requestHandler)
        client <- serviceConfig.client[IO]
        _ <- client ? request1
      } yield succeed
    }
  }

  "request-response service" should "reply with error" in {
    val serviceConfig = SqsRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](s"test-hello-error-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      _ <- service.start
      client <- serviceConfig.client[IO]
      r1 <- client ? request1
      r2 <- (client ? request2).map(_ => fail("expected exception")).handleErrorWith { case TestException("exception!!!") => IO(succeed) }
    } yield {
      assert(r1 === response1)
    }
  }

}
