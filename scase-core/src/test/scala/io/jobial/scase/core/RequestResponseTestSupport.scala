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

import cats.effect.IO
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

trait RequestResponseTestSupport extends AsyncFlatSpec
  with ScaseTestHelper
  with RequestResponseTestModel {

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

  implicit def mapping = new RequestResponseMapping[Req1, Resp1] {}

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

  def testSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    for {
      _ <- service.start
      r1 <- client.sendRequest(request1)
      r1 <- r1.response
      r11 <- client ? request1
      r2 <- client.sendRequest(request2)
      r2 <- r2.response
      r21 <- client ? request2
    } yield assert(
      response1 === r1.message && response1 === r11 &&
        response2 === r2.message && response2 === r21
    )
  }

  def testAnotherSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, Req, Resp]) = {
    for {
      _ <- service.start
      r <- client.sendRequest(Req1())
      r <- r.response
      r1 <- client ? Req1()
    } yield assert(Resp1() == r.message && Resp1() == r1)
  }

  def testTimeout(client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    IO.fromFuture(IO(recoverToSucceededIf[TimeoutException] {
      for {
        _ <- client ? request1
      } yield succeed
    }))
  }

  def testErrorReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    for {
      _ <- service.start
      r1 <- client ? request1
      r2 <- (client ? request2).map(_ => fail("expected exception")).handleErrorWith { case TestException("exception!!!") => IO(succeed) }
    } yield {
      assert(r1 === response1)
    }
  }

}