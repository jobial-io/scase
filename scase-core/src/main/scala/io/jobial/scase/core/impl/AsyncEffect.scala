package io.jobial.scase.core.impl

import cats.effect.Async
import cats.effect.kernel.Cont
import scala.concurrent.ExecutionContext

class AsyncEffect[F[_] : Async] extends TemporalEffect[F] with Async[F] {
  override def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] =
    Async[F].evalOn(fa, ec)

  override def executionContext: F[ExecutionContext] =
    Async[F].executionContext

  override def cont[K, R](body: Cont[F, K, R]): F[R] =
    Async[F].cont[K, R](body)

  override def never[A]: F[A] =
    super[Async].never[A]
}

object AsyncEffect {

  def apply[F[_] : Async] = new AsyncEffect[F]

  implicit def asyncEffect[F[_] : Async] = AsyncEffect[F]
}