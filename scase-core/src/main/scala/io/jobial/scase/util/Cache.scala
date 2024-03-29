package io.jobial.scase.util

import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.implicits.genConcurrentOps_
import cats.implicits._
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import java.lang.System.currentTimeMillis
import scala.concurrent.duration.Duration

class Cache[F[_] : ConcurrentEffect, A, B](store: Ref[F, Map[A, CacheEntry[F, A, B]]], timeout: Duration,
  accessCount: Ref[F, Long], cleanupFrequency: Int, maximumSize: Option[Int]) extends CatsUtils {

  def getOrCreate(key: A, value: F[B], onExpiry: (A, B) => F[Unit] = { (_: A, _: B) => unit[F] }): F[B] =
    for {
      accessCount <- accessCount.modify(c => (c + 1, c + 1))
      mv <- value.memoize
      v <- store.modify(s => {
        s.get(key) match {
          case Some(entry) =>
            (s + (key -> entry.copy(timestamp = currentTimeMillis)), entry.value)
          case None =>
            (s + (key -> CacheEntry(mv, currentTimeMillis, onExpiry)), mv)
        }
      })
      v <- v
      _ <- whenA(accessCount % cleanupFrequency == 0)(cleanup)
    } yield v

  def cleanup =
    for {
      //      _ <- maximumSize match {
      //        case Some(maximumSize) =>
      //          for {
      //            size <- store.get.map(_.size)
      //          } yield ()
      //        case None =>
      //          unit
      //      }
      expired <- store.modify { s =>
        s.partition { case (key, entry) =>
          currentTimeMillis - entry.timestamp < timeout.toMillis
        }
      }
      _ <- {
        for {
          (key, entry) <- expired.toList
        } yield for {
          value <- entry.value
          r <- entry.onExpiry(key, value)
        } yield r
      }.sequence
    } yield ()

  def size =
    for {
      s <- store.get
    } yield s.size
}

case class CacheEntry[F[_], A, B](
  value: F[B],
  timestamp: Long,
  onExpiry: (A, B) => F[Unit]
)

object Cache {

  def apply[F[_] : ConcurrentEffect, A, B](timeout: Duration = Duration.Inf, cleanupFrequency: Int = 100) = {
    for {
      store <- Ref.of[F, Map[A, CacheEntry[F, A, B]]](Map())
      accessCount <- Ref.of[F, Long](0)
    } yield new Cache[F, A, B](store, timeout, accessCount, cleanupFrequency, None)
  }
}