/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.core

import cats.Eq
import cats.effect.IO
import cats.tests.StrictCatsEquality
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

trait RequestResponseTestSupport extends AsyncFlatSpec
  with StrictCatsEquality
  with ScaseTestHelper
  with RequestResponseTestModel {

  val requestHandler = new TestRequestHandler {}

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

  def testSuccessfulReply[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Eq](client: RequestResponseClient[IO, REQ, RESP],
    request1: REQUEST, response1: RESPONSE)(implicit mapping: RequestResponseMapping[REQUEST, RESPONSE]): IO[Assertion] =
    for {
      r1 <- client.sendRequest(request1)
      m1 <- r1.response.message
      r11 <- client ? request1
    } yield assert(
      response1 === m1 && response1 === r11
    )

  def testSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]): IO[Assertion] = {
    for {
      h <- service.start
      //_ <- IO.sleep(1000.seconds)
      r1 <- testSuccessfulReply(client, request1, response1)
      r2 <- testSuccessfulReply(client, request2, response2)
      _ <- client.stop
      _ <- h.stop
    } yield r1
  }

  def testAnotherSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, Req, Resp]) = {
    for {
      h <- service.start
      r1 <- testSuccessfulReply(client, Req1(), Resp1())
      _ <- client.stop
      _ <- h.stop
    } yield r1
  }

  def testTimeout(client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    IO.fromFuture(IO(recoverToSucceededIf[TimeoutException] {
      for {
        _ <- (client ? request1).handleErrorWith { t =>
          for {
            _ <- client.stop
            _ <- IO.raiseError(t)
          } yield ()
        }
      } yield succeed
    }))
  }

  def testErrorReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    for {
      h <- service.start
      r1 <- client ? request1
      r2 <- (client ? request2).map(_ => fail("expected exception")).handleErrorWith { case TestException("exception!!!") => IO(succeed) }
      _ <- client.stop
      _ <- h.stop
    } yield {
      assert(r1 === response1)
    }
  }

}

trait TestRequestHandler extends RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] with RequestResponseTestModel {
  override def handleRequest(implicit context: RequestContext[IO]) = {
    case r: TestRequest1 =>
      println("replying...")
      r ! response1
    case r: TestRequest2 =>
      println("replying...")
      r ! response2
  }
}