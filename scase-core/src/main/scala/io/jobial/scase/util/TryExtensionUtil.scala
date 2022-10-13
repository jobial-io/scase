package io.jobial.scase.util

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Try extension that provides toEither in Scala 2.11.
 */
trait TryExtensionUtil {

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
}
