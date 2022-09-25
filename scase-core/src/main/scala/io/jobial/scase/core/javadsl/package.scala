package io.jobial.scase.core

import cats.effect.ContextShift
import cats.effect.IO
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import scala.collection.JavaConverters._

package object javadsl {
  val defaultSendRequestContext = io.jobial.scase.core.SendRequestContext()

  val defaultSendMessageContext = io.jobial.scase.core.SendMessageContext()


  def javaMapToScala[A, B](map: java.util.Map[A, B]) = map.asScala.toMap

  def scalaFutureToCompletableFuture[T](f: Future[T])(implicit ec: ExecutionContext) = {
    val completableFuture = new CompletableFuture[T]

    f.onComplete { r =>
      if (r.isSuccess) completableFuture.complete(r.get)
      else completableFuture.completeExceptionally(r.failed.get)
    }

    completableFuture
  }

  def completableFutureToIO[T](f: CompletableFuture[T])(implicit cs: ContextShift[IO]) =
    IO.fromFuture(IO(completableFutureToScalaFuture(f)))

  def completableFutureToScalaFuture[T](f: CompletableFuture[T]) = {
    val p = Promise[T]
    f.whenComplete {
      new BiConsumer[T, Throwable]() {
        override def accept(r: T, e: Throwable) =
          if (e != null) p.failure(e)
          else p.complete(Success(r))
      }
    }
    p.future
  }

  def javaFunctionToScala[A, B](f: java.util.function.Function[A, B]) = {
    a: A => f(a)
  }

  def javaRunnableToScala(f: java.lang.Runnable): () => Unit =
    () => f.run()
}
