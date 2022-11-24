package io.jobial.scase.util

import cats.effect.Concurrent
import cats.effect.Concurrent.memoize
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.impl.CatsUtils
import java.lang.System.currentTimeMillis
import scala.concurrent.duration.Duration

class Cache[F[_] : Concurrent, A, B](store: Ref[F, Map[A, CacheEntry[F, A, B]]], timeout: Duration,
  accessCount: Ref[F, Long]) extends CatsUtils {

  def getOrCreate(key: A, value: F[B], finalizer: (A, B) => F[Unit] = { (_: A, _: B) => unit[F] }): F[B] =
    for {
      mv <- memoize(value)
      v <- store.modify(s => {
        s.get(key) match {
          case Some(entry) =>
            (s, entry.value)
          case None =>
            (s + (key -> CacheEntry(mv, currentTimeMillis, finalizer)), mv)
        }
      })
      v <- v
    } yield v

  def cleanup =
    store.modify { s =>
      s.filter { case (key, entry) =>
        currentTimeMillis - entry.timestamp < timeout.toMillis
      } -> ()
    }

}

case class CacheEntry[F[_], A, B](
  value: F[B],
  timestamp: Long,
  finalizer: (A, B) => F[Unit]
)

object Cache {

  def apply[F[_] : Concurrent, A, B](timeout: Duration = Duration.Inf) = {
    for {
      store <- Ref.of[F, Map[A, CacheEntry[F, A, B]]](Map())
      accessCount <- Ref.of[F, Long](0)
    } yield new Cache[F, A, B](store, timeout, accessCount)
  }
}