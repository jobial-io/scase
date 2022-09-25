package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Sync
import cats.effect.Timer
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.catsSyntaxFlatMapOps
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import cats.implicits._

trait CatsUtils {

  def whenA[F[_] : Monad, A](cond: Boolean)(f: => F[A]): F[Unit] =
    if (cond) Monad[F].void(f) else Monad[F].unit

  // Monad would be enough here but Sync avoids some implicit clashes without causing any restrictions in practice
  def unit[F[_] : Sync] = Monad[F].unit

  // Monad would be enough here but Sync avoids some implicit clashes without causing any restrictions in practice
  def pure[F[_] : Sync, A](a: A) = Monad[F].pure(a)

  def raiseError[F[_] : Sync, A](t: Throwable) = Sync[F].raiseError[A](t)

  def delay[F[_] : Sync, A](f: => A) = Sync[F].delay(f)

  def defer[F[_] : Sync, A](f: => F[A]) = Sync[F].defer(f)

  def liftIO[F[_] : Concurrent, A](f: IO[A]) = Concurrent[F].liftIO(f)

  def fromFuture[F[_] : Concurrent, A](f: => Future[A]): F[A] =
    f.value match {
      case Some(result) =>
        result match {
          case Success(a) => pure(a)
          case Failure(e) => raiseError(e)
        }
      case _ =>
        Concurrent[F].async { cb =>
          f.onComplete(r => cb(r match {
            case Success(a) => Right(a)
            case Failure(e) => Left(e)
          }))(ExecutionContext.Implicits.global)
        }
    }

  def fromJavaFuture[F[_] : Concurrent, A](f: => java.util.concurrent.Future[A], pollTime: FiniteDuration = 10.millis): F[A] =
    delay(f.get(pollTime.toMillis, TimeUnit.MILLISECONDS)).handleErrorWith {
      case t: CancellationException =>
        raiseError(t)
      case t: ExecutionException =>
        raiseError(t.getCause)
      case _ =>
        fromJavaFuture(f, pollTime)
    }

  def waitFor[F[_] : Concurrent : Timer, A](f: => F[A])(cond: A => F[Boolean], pollTime: FiniteDuration = 1.second): F[A] =
    for {
      a <- f
      c <- cond(a)
      r <- if (c) pure(a) else Timer[F].sleep(pollTime) >> waitFor(f)(cond, pollTime)
    } yield r
}
