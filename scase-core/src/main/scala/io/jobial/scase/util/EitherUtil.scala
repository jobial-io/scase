package io.jobial.scase.util

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Extensions for Scala 2.11 support.
 */
trait EitherUtil {

  /**
   * This is only needed for Scala 2.11 compatibility.
   *
   * @param t
   * @tparam T
   */
  implicit class TryExtension[T](t: Try[T]) {

    def toEither = t match {
      case Success(value) =>
        Right(value)
      case Failure(t) =>
        Left(t)
    }
  }

  implicit class EitherExtension[+A, +B](t: Either[A, B]) {

    def toOption = t match {
      case Right(value) =>
        Some(value)
      case Left(_) =>
        None
    }
  }
}
