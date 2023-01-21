package io.jobial.scase.core.impl

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.Traverse
import cats.effect.Bracket
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.concurrent.MVar
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.catsSyntaxFlatMapOps

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

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

  def sleep[F[_] : Timer](duration: FiniteDuration) = Timer[F].sleep(duration)

  def start[F[_] : Concurrent, A](f: F[A]) = Concurrent[F].start(f)

  def fromFuture[F[_] : Concurrent, A](f: => Future[A]): F[A] = {
    delay(f).flatMap { f =>
      f.value match {
        case Some(result) =>
          result match {
            case Success(a) => pure(a)
            case Failure(e) => raiseError(e)
          }
        case _ =>
          Concurrent[F].async { cb =>
            f.onComplete { r =>
              cb(r match {
                case Success(a) => Right(a)
                case Failure(e) => Left(e)
              })
            }(blockerContext)
          }
      }
    }
  }

  def fromEither[F[_] : Concurrent, A](e: Either[Throwable, A]): F[A] =
    e match {
      case Right(a) => pure(a)
      case Left(err) => raiseError(err)
    }

  def fromJavaFuture[F[_] : Concurrent, A](future: => java.util.concurrent.Future[A], pollTime: FiniteDuration = 10.millis): F[A] =
    for {
      f <- delay(future)
      r <- delay(f.get(pollTime.toMillis, TimeUnit.MILLISECONDS)).handleErrorWith {
        case t: CancellationException =>
          raiseError(t)
        case t: ExecutionException =>
          raiseError(t.getCause)
        case _ =>
          fromJavaFuture(f, pollTime)
      }
    } yield r

  def waitFor[F[_] : Concurrent : Timer, A](f: => F[A])(cond: A => F[Boolean], pollTime: FiniteDuration = 1.second): F[A] =
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

  def take[F[_] : Concurrent : Timer, T](mvar: MVar[F, T], timeout: Option[FiniteDuration], pollTime: FiniteDuration = 1.millis): F[T] =
    timeout match {
      case Some(timeout) =>
        for {
          r <- mvar.tryTake
          r <- r match {
            case Some(r) =>
              pure(r)
            case None =>
              if (timeout > 0.millis)
                sleep(pollTime) >>
                  take(mvar, Some(timeout - pollTime))
              else raiseError(new TimeoutException)
          }
        } yield r
      case None =>
        mvar.take
    }

  def guarantee[F[_], A](fa: F[A])(finalizer: F[Unit])(implicit bracket: Bracket[F, Throwable]): F[A] =
    Bracket[F, Throwable].guarantee(fa)(finalizer)
}