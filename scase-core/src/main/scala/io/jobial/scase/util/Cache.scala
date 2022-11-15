package io.jobial.scase.util

import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.implicits.genConcurrentOps_
import cats.implicits._

class Cache[F[_] : Concurrent, A, B](store: Ref[F, Map[A, F[B]]]) {

  def getOrCreate(key: A, value: F[B]): F[B] =
    for {
      mv <- value.memoize
      v <- store.modify(s => {
        s.get(key) match {
          case Some(v) =>
            (s, v)
          case None =>
            (s + (key -> mv), mv)
        }
      })
      v <- v
    } yield v
}

object Cache {

  def apply[F[_] : Concurrent, A, B] = {
    for {
      store <- Ref.of[F, Map[A, F[B]]](Map())
    } yield new Cache[F, A, B](store)
  }
}