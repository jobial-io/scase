package io.jobial.scase.inmemory

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.jobial.scase.core.{ScaseTestHelper, TestRequest1}
import io.jobial.scase.marshalling.serialization._
import org.scalatest.flatspec.AsyncFlatSpec

class InMemoryQueueTest extends AsyncFlatSpec with ScaseTestHelper {

  "request-response service" should "reply" in {
    val request = TestRequest1("1")

    for {
      queue <- InMemoryConsumerProducer[IO, TestRequest1]()
      d <- Deferred[IO, TestRequest1]
      _ <- queue.subscribe({ m =>
        println(m)
        d.complete(m.message)
      })
      _ <- queue.send(request)
      r <- d.get
      _ = println(r)
    } yield assert(r == request)
  }
}
