package io.jobial.scase.core

import cats._
import cats.implicits._
import cats.syntax._
import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.jobial.scase.inmemory.InMemoryQueue
import io.jobial.scase.marshalling.serialization._
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.ExecutionContext

class ConsumerProducerRequestResponseServiceTest extends AsyncFlatSpec {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  val request1 = TestRequest1("1")

  val request2 = TestRequest2("2")

  val response1 = TestResponse1(request1, "1")

  implicit def runIOResult(r: IO[Assertion]) = r.unsafeToFuture

  case object TestException extends Exception

  def testRequestResponse[REQ, RESP](testRequestProcessor: RequestProcessor[REQ, RESP], request: REQ, response: Either[Throwable, RESP]) =
    for {
      testMessageConsumer <- InMemoryQueue.create[REQ]
      testMessageProducer <- InMemoryQueue.create[Either[Throwable, RESP]]
      service = ConsumerProducerRequestResponseService[REQ, RESP](
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


  "request-response service" should "reply successfully" in {
    testRequestResponse(
      new RequestProcessor[TestRequest, TestResponse] {
        override def processRequest(implicit context: RequestContext): Processor = {
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
      new RequestProcessor[TestRequest, TestResponse] {
        override def processRequest(implicit context: RequestContext): Processor = {
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

}
