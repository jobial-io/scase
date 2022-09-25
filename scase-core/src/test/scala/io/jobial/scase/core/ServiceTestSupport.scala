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
import cats.effect.IO.raiseError
import cats.effect.concurrent.Deferred
import cats.tests.StrictCatsEquality
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

trait ServiceTestSupport extends AsyncFlatSpec
  with StrictCatsEquality
  with ScaseTestHelper
  with ServiceTestModel
  with Logging {

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
        raiseError(TestException("exception!!!"))
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

  def testSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]): IO[Assertion] =
    for {
      h <- service.start
      //_ <- IO.sleep(1000.seconds)
      r1 <- testSuccessfulReply(client, request1, response1)
      r2 <- testSuccessfulReply(client, request2, response2)
      _ <- client.stop
      _ <- h.stop
    } yield r1

  def testAnotherSuccessfulReply(service: Service[IO], client: RequestResponseClient[IO, Req, Resp]) =
    for {
      h <- service.start
      r1 <- testSuccessfulReply(client, Req1(), Resp1())
      _ <- client.stop
      _ <- h.stop
    } yield r1

  def testSuccessfulStreamReply[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Unmarshaller : Eq](
    senderClient: SenderClient[IO, REQ],
    request1: REQUEST, response1: RESPONSE,
    receiverClient: ReceiverClient[IO, Either[Throwable, RESPONSE]]
  ): IO[Assertion] =
    for {
      _ <- senderClient.send(request1)
      r1 <- receiverClient.receiveWithContext
      m1 <- r1.message
      _ <- senderClient ! request1
      m2 <- receiverClient.receive
    } yield assert(
      response1 === m1.right.get && response1 === m2.right.get
    )

  def testSuccessfulStreamReply[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Unmarshaller : Eq](
    senderClient: SenderClient[IO, REQ],
    request1: REQUEST, response1: RESPONSE,
    responseReceiverClient: ReceiverClient[IO, RESPONSE],
    errorReceiverClient: ReceiverClient[IO, Throwable]
  ): IO[Assertion] =
    for {
      _ <- senderClient.send(request1)
      _ = println(s"xxxsent $request1")
      r1 <- responseReceiverClient.receiveWithContext
      _ = println(s"xxxreceived $r1")
      m1 <- r1.message
      _ <- senderClient ! request1
      _ = println(s"xxxsent $r1")
      m2 <- responseReceiverClient.receive
      _ = println(s"xxxreceived $m2")
      _ <- errorReceiverClient.receive(2.second).map(_ => fail()).handleErrorWith { case t: ReceiveTimeout => error[IO](s"receiver received error", t) }
    } yield assert(
      response1 === m1 && response1 === m2
    )

  def testSuccessfulStreamReply(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, Either[Throwable, TestResponse]]
  )(implicit u: Unmarshaller[TestResponse]): IO[Assertion] = {
    for {
      h <- service.start
      r1 <- testSuccessfulStreamReply(senderClient, request1, response1, receiverClient)
      r2 <- testSuccessfulStreamReply(senderClient, request2, response2, receiverClient)
      _ <- senderClient.stop
      _ <- receiverClient.stop
      _ <- h.stop
    } yield r1
  }

  def testSuccessfulStreamReply(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    responseReceiverClient: ReceiverClient[IO, TestResponse],
    errorReceiverClient: ReceiverClient[IO, Throwable]
  )(implicit u: Unmarshaller[TestResponse]): IO[Assertion] = {
    for {
      h <- service.start
      r1 <- testSuccessfulStreamReply(senderClient, request1, response1, responseReceiverClient, errorReceiverClient)
      r2 <- testSuccessfulStreamReply(senderClient, request2, response2, responseReceiverClient, errorReceiverClient)
      _ <- senderClient.stop
      _ <- responseReceiverClient.stop
      _ <- errorReceiverClient.stop
      _ <- h.stop
    } yield r1
  }

  def testRequestResponseTimeout(client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[RequestTimeout] {
      for {
        _ <- (client ? request1).handleErrorWith { t =>
          for {
            _ <- client.stop
            _ <- raiseError(t)
          } yield ()
        }
      } yield succeed
    }
  }

  def testStreamTimeout(
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, TestResponse]
  ) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[TimeoutException] {
      for {
        _ <- senderClient ! request1
        _ <- receiverClient.receive(1.second).handleErrorWith { t =>
          for {
            _ <- senderClient.stop
            _ <- receiverClient.stop
            _ <- raiseError(t)
          } yield ()
        }
      } yield succeed
    }
  }

  def testRequestResponseErrorReply(service: Service[IO], client: RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]) =
    for {
      h <- service.start
      r1 <- client ? request1
      r2 <- (client ? request2).map(_ => fail("expected exception")).handleErrorWith { case TestException("exception!!!") => IO(succeed) }
      _ <- client.stop
      _ <- h.stop
    } yield {
      assert(r1 === response1)
    }

  def testStreamErrorReply(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, Either[Throwable, TestResponse]]
  ) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[ReceiveTimeout] {
      for {
        _ <- senderClient ! request1
        _ <- receiverClient.receive(1.second).map(_ => fail("expected exception"))
      } yield succeed
    }
  }

  def testSuccessfulMessageHandlerReceive(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receivedMessage: Deferred[IO, TestRequest[_ <: TestResponse]]
  ) =
    for {
      h <- service.start
      _ <- senderClient ! request1
      r <- receivedMessage.get
      _ <- senderClient.stop
      _ <- h.stop
    } yield assert(r.asInstanceOf[TestRequest1] === request1)

  def testMessageSourceReceive(
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, TestRequest[_ <: TestResponse]]
  ) =
    for {
      _ <- senderClient ! request1
      r <- receiverClient.receive
      _ <- senderClient.stop
      _ <- receiverClient.stop
    } yield assert(r.asInstanceOf[TestRequest1] === request1)
}

trait TestRequestHandler extends RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] with ServiceTestModel {
  override def handleRequest(implicit context: RequestContext[IO]) = {
    case r: TestRequest1 =>
      println("replying...")
      r ! response1
    case r: TestRequest2 =>
      println("replying...")
      r ! response2
  }
}

case class TestMessageHandler(receivedMessage: Deferred[IO, TestRequest[_ <: TestResponse]]) extends MessageHandler[IO, TestRequest[_ <: TestResponse]] with ServiceTestModel {
  override def handleMessage(implicit context: MessageContext[IO]) = {
    case r: TestRequest1 =>
      println("received...")
      receivedMessage.complete(r)
    case r: TestRequest2 =>
      println("received...")
      receivedMessage.complete(r)
  }
}