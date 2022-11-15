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
package io.jobial.scase.core.impl

import cats.Eq
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.IO.raiseError
import cats.implicits._
import io.jobial.scase.core._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestException
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.core.test.TestRequest2
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.inmemory.InMemoryConsumer
import io.jobial.scase.marshalling.serialization._

class ConsumerProducerRequestResponseServiceTest
  extends ServiceTestSupport {

  def testRequestResponse[REQ, RESP](testRequestProcessor: RequestHandler[IO, REQ, RESP], request: REQ, response: Either[Throwable, RESP]) =
    for {
      testMessageConsumer <- InMemoryConsumer[IO, REQ]
      requestProducer <- testMessageConsumer.producer
      resultConsumer <- InMemoryConsumer[IO, Either[Throwable, RESP]]
      testMessageProducer <- resultConsumer.producer
      service <- ConsumerProducerRequestResponseService[IO, REQ, RESP](
        testMessageConsumer,
        { _: Option[String] => IO(testMessageProducer) },
        testRequestProcessor
      )
      s <- service.start
      d <- Deferred[IO, Either[Throwable, RESP]]
      _ <- resultConsumer.subscribe({ m =>
        for {
          message <- m.message
          r <- d.complete(message)
        } yield r
      })
      _ <- requestProducer.send(request, Map(ResponseProducerIdKey -> ""))
      r <- d.get
    } yield assert(r == response)

  def testRequestResponseClient[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Eq](testRequestProcessor: RequestHandler[IO, REQ, RESP], request: REQUEST, response: Either[Throwable, RESPONSE])(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  ) =
    for {
      testMessageConsumer <- InMemoryConsumer[IO, REQ]
      requestProducer <- testMessageConsumer.producer
      resultConsumer <- InMemoryConsumer[IO, Either[Throwable, RESP]]
      testMessageProducer <- resultConsumer.producer
      service <- ConsumerProducerRequestResponseService[IO, REQ, RESP](
        testMessageConsumer,
        { _: Option[String] => IO(testMessageProducer) },
        testRequestProcessor
      )
      s <- service.start
      client <- ConsumerProducerRequestResponseClient[IO, REQ, RESP](
        resultConsumer,
        () => requestProducer,
        None
      )
      r1 <- client ? request
      r <- {
        implicit val c = client
        // Do it this way to test if the implicit request sending is working, obviously we could also use client.sendRequest here 
        for {
          r <- request
        } yield r
      }
    } yield assert(Right(r) === response)

  "request-response service" should "reply successfully" in {
    testRequestResponse(
      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def handleRequest(implicit context: RequestContext[IO]): Handler = {
          case r: TestRequest1 =>
            r.reply(response1)
        }
      },
      request1,
      Right(response1)
    )
  }

  "request-response service" should "reply with error" in {
    testRequestResponse(
      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def handleRequest(implicit context: RequestContext[IO]): Handler = {
          case r: TestRequest1 =>
            r.reply(response1)
          case r: TestRequest2 =>
            raiseError(TestException("exception!!!"))
        }
      },
      request2,
      Left(TestException("exception!!!"))
    )
  }

  "request-response client" should "get reply successfully" in {
    testRequestResponseClient(
      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def handleRequest(implicit context: RequestContext[IO]) = {
          case r: TestRequest1 =>
            r.reply(response1)
        }
      },
      request1,
      Right(response1)
    )
  }

}
