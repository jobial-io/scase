package io.jobial.scase.logging

import cats.effect.Sync
import com.typesafe.scalalogging.LazyLogging


trait Logging extends LazyLogging {

  def trace[F[_] : Sync](msg: => String) = Sync[F].delay(logger.trace(msg))

  def debug[F[_] : Sync](msg: => String) = Sync[F].delay(logger.debug(msg))

  def info[F[_] : Sync](msg: => String) = Sync[F].delay(logger.info(msg))

  def warn[F[_] : Sync](msg: => String) = Sync[F].delay(logger.warn(msg))

  def error[F[_] : Sync](msg: => String) = Sync[F].delay(logger.error(msg))

  def error[F[_] : Sync](msg: => String, t: Throwable) = Sync[F].delay(logger.error(msg, t))
}