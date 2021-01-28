package io.jobial.scase.core

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.jobial.scase.inmemory.InMemoryQueue
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.marshalling.serialization._
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.ExecutionContext

class ConsumerProducerRequestResponseServiceTest extends AsyncFlatSpec {

  "request-response service" should "reply" in {
    val request1 = TestRequest1("1")

    val response1 = TestResponse1(request1, "1")

    implicit val cs = IO.contextShift(ExecutionContext.global)

    (for {
      testMessageConsumer <- InMemoryQueue.create[TestRequest]
      testMessageProducer <- InMemoryQueue.create[Either[Throwable, TestResponse]]
      testRequestProcessor = new RequestProcessor[TestRequest, TestResponse] {
        override def processRequest(implicit context: RequestContext): Processor = {
          case r: TestRequest1 =>
            println("replying...")
            r.reply(response1)
        }
      }
      service = ConsumerProducerRequestResponseService[TestRequest, TestResponse](
        testMessageConsumer,
        { _ => IO(testMessageProducer) },
        testRequestProcessor
      )
      s <- service.startService
      d <- Deferred[IO, Either[Throwable, TestResponse]]
      _ <- testMessageProducer.subscribe({ m =>
        println("complete")
        d.complete(m.message)
      })
      _ <- testMessageConsumer.send(request1, Map(ResponseConsumerIdKey -> ""))
      r <- d.get
    } yield assert(r == Right(response1))).unsafeToFuture()

  }
}
