package io.jobial.scase.logging

import cats.Monad
import cats.effect.Concurrent
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging


trait Logging extends LazyLogging {
  
  def trace[F[_]: Monad](msg: => String) = Monad[F].pure(logger.trace(msg))

  def debug[F[_]: Monad](msg: => String) = Monad[F].pure(logger.debug(msg))

  def info[F[_]: Monad](msg: => String) = Monad[F].pure(logger.info(msg))

  def warn[F[_]: Monad](msg: => String) = Monad[F].pure(logger.warn(msg))

  def error[F[_]: Monad](msg: => String) = Monad[F].pure(logger.error(msg))

  def error[F[_]: Monad](msg: => String, t: Throwable) = Monad[F].pure(logger.error(msg, t))
}