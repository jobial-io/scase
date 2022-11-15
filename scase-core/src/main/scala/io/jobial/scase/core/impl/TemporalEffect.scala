package io.jobial.scase.core.impl

import cats.effect.Sync
import cats.effect.Temporal
import scala.concurrent.duration.FiniteDuration

class TemporalEffect[F[_] : Sync : Temporal] extends ConcurrentEffect[F] with Temporal[F] {

  override def sleep(time: FiniteDuration): F[Unit] =
    Temporal[F].sleep(time)
}

object TemporalEffect {

  def apply[F[_] : Sync : Temporal] = new TemporalEffect[F]

  implicit def temporalEffect[F[_] : Sync : Temporal] = TemporalEffect[F]
}