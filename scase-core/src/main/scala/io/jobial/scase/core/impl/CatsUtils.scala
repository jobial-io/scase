package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

trait CatsUtils {

  def fromFuture[F[_] : Concurrent, A](f: => Future[A]): F[A] =
    f.value match {
      case Some(result) =>
        result match {
          case Success(a) => Concurrent[F].pure(a)
          case Failure(e) => Concurrent[F].raiseError(e)
        }
      case _ =>
        Concurrent[F].async { cb =>
          f.onComplete(r => cb(r match {
            case Success(a) => Right(a)
            case Failure(e) => Left(e)
          }))(ExecutionContext.Implicits.global)
        }
    }

  def whenA[F[_] : Monad, A](cond: Boolean)(f: => F[A]): F[Unit] =
    if (cond) Monad[F].void(f) else Monad[F].unit

}
