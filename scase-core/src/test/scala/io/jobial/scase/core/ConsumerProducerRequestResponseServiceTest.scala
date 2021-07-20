package io.jobial.scase.core

import cats.effect.IO
import cats.effect.concurrent.Deferred
import cats.implicits._
import io.jobial.scase.inmemory.InMemoryQueue
import io.jobial.scase.marshalling.serialization._

import scala.concurrent.duration.DurationInt


class ConsumerProducerRequestResponseServiceTest
  extends RequestResponseTestSupport {

  def testRequestResponse[REQ, RESP](testRequestProcessor: RequestProcessor[IO, REQ, RESP], request: REQ, response: Either[Throwable, RESP]) =
    for {
      testMessageConsumer <- InMemoryQueue[IO, REQ]
      testMessageProducer <- InMemoryQueue[IO, Either[Throwable, RESP]]
      service = ConsumerProducerRequestResponseService[IO, REQ, RESP](
        testMessageConsumer,
        { _ => IO(testMessageProducer) },
        testRequestProcessor
      )
      s <- service.startService
      d <- Deferred[IO, Either[Throwable, RESP]]
      _ <- testMessageProducer.subscribe({ m =>
        println("complete")
        d.complete(m.message)
      })
      _ <- testMessageConsumer.send(request, Map(ResponseConsumerIdKey -> ""))
      r <- d.get
    } yield assert(r == response)

  implicit val sendRequestContext = SendRequestContext(Some(10.seconds))

  def testRequestResponseClient[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP](testRequestProcessor: RequestProcessor[IO, REQ, RESP], request: REQUEST, response: Either[Throwable, RESPONSE])(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  ) =
    for {
      testMessageConsumer <- InMemoryQueue[IO, REQ]
      testMessageProducer <- InMemoryQueue[IO, Either[Throwable, RESP]]
      service = ConsumerProducerRequestResponseService[IO, REQ, RESP](
        testMessageConsumer,
        { _ => IO(testMessageProducer) },
        testRequestProcessor
      )
      s <- service.startService
      client <- ConsumerProducerRequestResponseClient[IO, REQ, RESP](
        testMessageProducer,
        () => testMessageConsumer,
        ""
      )
      r <- {
        implicit val c = client

        // We do it this way to test if the implicit request sending is working, obviously we could also use client.sendRequest here 
        for {
          r <- request
        } yield r
      }
    } yield assert(Right(r) == response)

  "request-response service" should "reply successfully" in {
    testRequestResponse(
      new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def processRequest(implicit context: RequestContext[IO]): Processor = {
          case r: TestRequest1 =>
            println("replying...")
            r.reply(response1)
        }
      },
      request1,
      Right(response1)
    )
  }

  "request-response service" should "reply with error" in {
    testRequestResponse(
      new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def processRequest(implicit context: RequestContext[IO]): Processor = {
          case r: TestRequest1 =>
            println("replying...")
            r.reply(response1)
          case r: TestRequest2 =>
            IO.raiseError(TestException)
        }
      },
      request2,
      Left(TestException)
    )
  }

  "request-response client" should "get reply successfully" in {
    testRequestResponseClient(
      new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
        override def processRequest(implicit context: RequestContext[IO]) = {
          case r: TestRequest1 =>
            println("replying...")
            r.reply(response1)
        }
      },
      request1,
      Right(response1)
    )
  }

}
