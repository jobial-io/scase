package io.jobial.scase.inmemory

import cats.effect.IO
import cats.effect.concurrent.{Deferred, Ref}
import io.jobial.scase.core.{ExecutionContextWithShutdown, MessageReceiveResult, TestRequest, TestRequest1, TestResponse, TestResponse1}
import io.jobial.scase.marshalling.serialization._
import org.scalatest.flatspec.AsyncFlatSpec
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

class InMemoryQueueTest extends AsyncFlatSpec {

  "request-response service" should "reply" in {
    val request = TestRequest1("1")
    implicit val cs = IO.contextShift(ExecutionContextWithShutdown(Executors.newCachedThreadPool))

    (for {
      queue <- InMemoryQueue.create[TestRequest1]
      d <- Deferred[IO, TestRequest1]
      _ <- queue.subscribe({ m =>
        println(m)
        d.complete(m.message)
      })
      _ <- queue.send(request)
      r <- d.get
      _ = println(r)
    } yield assert(r == request)).unsafeToFuture()

  }
}
