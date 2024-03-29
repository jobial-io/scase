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
package io.jobial.scase.core.test

import cats.Eq
import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.raiseError
import cats.effect.IO.whenA
import cats.effect.Ref
import cats.effect.std.Queue
import cats.tests.StrictCatsEquality
import io.jobial.scase.core.MessageContext
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestContext
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.RequestTimeout
import io.jobial.scase.core.SendRequestContext
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.Service
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.marshalling.rawbytes.iterableToSequenceSyntax
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import scala.concurrent.duration._

trait ServiceTestSupport extends AsyncFlatSpec
  with StrictCatsEquality
  with ScaseTestHelper
  with ServiceTestModel
  with Logging {

  val requestHandler = new TestRequestHandler {}

  implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(30.seconds))

  val anotherRequestProcessor = RequestHandler[IO, Req, Resp](implicit context => {
    case r: Req1 =>
      import io.jobial.scase.core.RequestExtension
      // this is to make sure extension works
      delay(r.attributes) >>
        delay(r.underlyingContext[Any]).attempt >>
        delay(r.underlyingMessage[Any]).attempt >>
        r.reply(Resp1())
  })

  val requestHandlerWithError = new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def handleRequest(implicit context: RequestContext[IO]) = {
      case r: TestRequest1 =>
        r ! response1
      case r: TestRequest2 =>
        raiseError(TestException("exception!!!"))
    }
  }

  val anotherMessageHandler = MessageHandler[IO, Req](implicit context => {
    case r: Req1 =>
      import io.jobial.scase.core.MessageExtension
      // this is to make sure extension works
      delay(r.attributes) >>
        delay(r.underlyingContext[Any]).attempt >>
        delay(r.underlyingMessage[Any]).attempt >>
        delay(println(s"received $r in $this"))
  })

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
      //_ <- IO.sleep(2.seconds)
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

  def testSenderReceiver[M: Eq](
    senderClient: SenderClient[IO, M],
    receiverClient: ReceiverClient[IO, M],
    message: M
  ): IO[Assertion] =
    for {
      _ <- senderClient.send(message)
      r1 <- receiverClient.receiveWithContext
      m1 <- r1.message
      _ <- senderClient ! message
      m2 <- receiverClient.receive
    } yield assert(
      message === m1 && message === m2
    )

  def testSuccessfulStreamReply[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Unmarshaller : Eq](
    senderClient: SenderClient[IO, REQ],
    request1: REQUEST,
    response1: RESPONSE,
    receiverClient: ReceiverClient[IO, Either[Throwable, RESPONSE]],
    startReceivingBeforeSend: Boolean
  ): IO[Assertion] =
    for {
      _ <- whenA(startReceivingBeforeSend)(receiverClient.receive(1.millis).handleErrorWith(t => IO.unit) >> IO.unit)
      _ <- senderClient.send(request1)
      r1 <- receiverClient.receiveWithContext
      m1 <- r1.message
      _ <- senderClient ! request1
      m2 <- receiverClient.receive
    } yield assert(
      response1 === m1.right.get && response1 === m2.right.get
    )

  def testSuccessfulStreamErrorReply[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Unmarshaller : Eq](
    senderClient: SenderClient[IO, REQ],
    request1: REQUEST, response1: RESPONSE,
    responseReceiverClient: ReceiverClient[IO, RESPONSE],
    errorReceiverClient: ReceiverClient[IO, Throwable]
  ): IO[Assertion] =
    for {
      _ <- senderClient.send(request1)
      r1 <- responseReceiverClient.receiveWithContext
      m1 <- r1.message
      _ <- senderClient ! request1
      m2 <- responseReceiverClient.receive
      _ <- errorReceiverClient.receive(1.second).map(_ => fail()).handleErrorWith { case t: ReceiveTimeout => error[IO](s"receiver received error", t) }
    } yield assert(
      response1 === m1 && response1 === m2
    )

  def testSuccessfulStreamReply(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, Either[Throwable, TestResponse]],
    startReceivingBeforeSend: Boolean = false
  )(implicit u: Unmarshaller[TestResponse]): IO[Assertion] = {
    for {
      h <- service.start
      r1 <- testSuccessfulStreamReply(senderClient, request1, response1, receiverClient, startReceivingBeforeSend)
      r2 <- testSuccessfulStreamReply(senderClient, request2, response2, receiverClient, startReceivingBeforeSend)
      _ <- senderClient.stop
      _ <- receiverClient.stop
      _ <- h.stop
    } yield r1
  }

  def testSuccessfulStreamErrorReply(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    responseReceiverClient: ReceiverClient[IO, TestResponse],
    errorReceiverClient: ReceiverClient[IO, Throwable],
    startReceivingBeforeSend: Boolean = false
  )(implicit u: Unmarshaller[TestResponse]): IO[Assertion] = {
    for {
      h <- service.start
      r1 <- testSuccessfulStreamErrorReply(senderClient, request1, response1, responseReceiverClient, errorReceiverClient)
      r2 <- testSuccessfulStreamErrorReply(senderClient, request2, response2, responseReceiverClient, errorReceiverClient)
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

    recoverToSucceededIf[ReceiveTimeout] {
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
    receiverClient: ReceiverClient[IO, Either[Throwable, TestResponse]],
    startReceivingBeforeSend: Boolean = false
  ) = {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[ReceiveTimeout] {
      for {
        _ <- whenA(startReceivingBeforeSend)(receiverClient.receive(1.millis).handleErrorWith(t => IO.unit) >> IO.unit)
        _ <- senderClient ! request1
        _ <- receiverClient.receive(1.second).map(_ => fail("expected exception"))
      } yield succeed
    }
  }

  def testSuccessfulMessageHandlerReceive(
    service: Service[IO],
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receivedMessage: Queue[IO, TestRequest[_ <: TestResponse]],
    stopService: Boolean = true
  ) =
    for {
      h <- service.start
      _ <- senderClient ! request1
      r <- receivedMessage.take
      _ <- senderClient.stop
      _ <- whenA(stopService)(h.stop >> IO.unit)
    } yield assert(r.asInstanceOf[TestRequest1] === request1)

  def testMessageSourceReceive(
    senderClient: SenderClient[IO, TestRequest[_ <: TestResponse]],
    receiverClient: ReceiverClient[IO, TestRequest[_ <: TestResponse]],
    startReceivingBeforeSend: Boolean = false
  ) =
    for {
      _ <- whenA(startReceivingBeforeSend)(receiverClient.receive(1.millis).handleErrorWith(t => IO.unit) >> IO.unit)
      _ <- senderClient ! request1
      r <- receiverClient.receive
      _ <- senderClient.stop
      _ <- receiverClient.stop
    } yield assert(r.asInstanceOf[TestRequest1] === request1)

  def testMultipleRequests[REQ, RESP: Eq](
    service: Service[IO],
    client: IO[RequestResponseClient[IO, REQ, RESP]],
    request: Int => REQ, response: Int => RESP,
    requestCount: Int = 100
  )(implicit mapping: RequestResponseMapping[REQ, RESP]): IO[List[Assertion]] =
    for {
      received <- Ref.of[IO, List[Int]](List())
      range = 0 until requestCount
      h <- service.start
      r <- {
        for {
          i <- range
        } yield for {
          client <- client
          _ <- debug[IO](s"sending $i")
          r <- testSuccessfulReply(client, request(i), response(i))
          _ <- received.update(i :: _)
          _ <- debug[IO](s"received $i")
        } yield r
      }.toList.parSequence.handleErrorWith { t => {
        for {
          received <- received.get
          _ <- error[IO](s"missing responses: " + (range.diff(received)))
        } yield ()
      } >> raiseError(t)
      }
      _ <- h.stop
    } yield r
}

trait TestRequestHandler extends RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] with ServiceTestModel {
  override def handleRequest(implicit context: RequestContext[IO]) = {
    case r: TestRequest1 =>
      r ! TestResponse1(r, r.id)
    case r: TestRequest2 =>
      r ! TestResponse2(r, r.id)
  }
}

case class TestMessageHandler(receivedMessage: Queue[IO, TestRequest[_ <: TestResponse]]) extends MessageHandler[IO, TestRequest[_ <: TestResponse]] with ServiceTestModel {
  override def handleMessage(implicit context: MessageContext[IO]) = {
    case r: TestRequest1 =>
      receivedMessage.offer(r)
    case r: TestRequest2 =>
      receivedMessage.offer(r)
  }
}