package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.effect.Sync
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
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
}
