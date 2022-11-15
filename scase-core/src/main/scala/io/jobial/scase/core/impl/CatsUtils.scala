package io.jobial.scase.core.impl

import cats.Applicative
import cats.Monad
import cats.MonadError
import cats.Parallel
import cats.Traverse
import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.MonadCancel
import cats.effect.Sync
import cats.effect.Temporal
import cats.effect.std.Queue
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.catsSyntaxFlatMapOps
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

trait CatsUtils {

  def whenA[F[_] : Monad, A](cond: Boolean)(f: => F[A]): F[Unit] =
    if (cond) Monad[F].void(f) else Monad[F].unit

  // Monad would be enough here but Sync avoids some implicit clashes without causing any restrictions in practice
  def unit[F[_] : Sync] = Monad[F].unit

  // Monad would be enough here but Sync avoids some implicit clashes without causing any restrictions in practice
  def pure[F[_] : Sync, A](a: A) = Monad[F].pure(a)

  type MonadErrorWithThrowable[F[_]] = MonadError[F, Throwable]

  def raiseError[F[_] : ConcurrentEffect, A](t: Throwable) = implicitly[MonadErrorWithThrowable[F]].raiseError[A](t)

  def delay[F[_] : ConcurrentEffect, A](f: => A) = Sync[F].delay(f)

  def blocking[F[_] : ConcurrentEffect, A](f: => A) = Sync[F].blocking(f)

  def defer[F[_] : ConcurrentEffect, A](f: => F[A]) = Sync[F].defer(f)

  def liftIO[F[_] : LiftIO, A](f: IO[A]) = LiftIO[F].liftIO(f)

  def sleep[F[_] : Temporal](duration: FiniteDuration) = Temporal[F].sleep(duration)

  def start[F[_] : ConcurrentEffect, A](f: F[A]) = Concurrent[F].start(f)

  def fromFuture[F[_] : Async, A](f: => Future[A]): F[A] =
    Async[F].async { cb =>
      f.onComplete(r => cb(r match {
        case Success(a) => Right(a)
        case Failure(e) => Left(e)
      }))(ExecutionContext.Implicits.global)
      pure[F, Option[F[Unit]]](None)
    }

  def fromEither[F[_] : ConcurrentEffect, A](e: Either[Throwable, A]): F[A] =
    e match {
      case Right(a) => pure[F, A](a)
      case Left(err) => raiseError(err)
    }

  def fromJavaFuture[F[_] : TemporalEffect, A](future: => java.util.concurrent.Future[A], pollTime: FiniteDuration = 10.millis): F[A] =
    for {
      f <- delay(future)
      r <- blocking(f.get(pollTime.toMillis, TimeUnit.MILLISECONDS)).handleErrorWith {
        case t: CancellationException =>
          raiseError(t)
        case t: ExecutionException =>
          raiseError(t.getCause)
        case _ =>
          fromJavaFuture(f, pollTime)
      }
    } yield r

  def waitFor[F[_] : TemporalEffect, A](f: => F[A])(cond: A => F[Boolean], pollTime: FiniteDuration = 1.second): F[A] =
    for {
      a <- f
      c <- cond(a)
      r <- if (c) pure(a) else sleep(pollTime) >> waitFor(f)(cond, pollTime)
    } yield r

  case class IterableSequenceSyntax[F[_] : Parallel : Applicative, T](l: Iterable[F[T]]) {

    def parSequence = Parallel.parSequence(l.toList)

    def sequence = Traverse[List].sequence(l.toList)
  }

  implicit def iterableToSequenceSyntax[F[_] : Parallel : Applicative, T](l: Iterable[F[T]]) =
    IterableSequenceSyntax(l)

  def take[F[_] : TemporalEffect, T](queue: Queue[F, T], timeout: Option[FiniteDuration], pollTime: FiniteDuration = 1.millis): F[T] =
    timeout match {
      case Some(timeout) =>
        for {
          r <- queue.tryTake
          r <- r match {
            case Some(r) =>
              pure[F, T](r)
            case None =>
              if (timeout > 0.millis)
                sleep(pollTime) >>
                  take(queue, Some(timeout - pollTime))
              else raiseError(new TimeoutException)
          }
        } yield r
      case None =>
        queue.take
    }

  def guarantee[F[_], A](fa: F[A])(finalizer: F[Unit])(implicit bracket: MonadCancel[F, Throwable]): F[A] =
    MonadCancel[F, Throwable].guarantee(fa, finalizer)
}




