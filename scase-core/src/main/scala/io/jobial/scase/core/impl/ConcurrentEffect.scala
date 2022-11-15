package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.Sync
import cats.effect.kernel.Deferred
import cats.effect.kernel.Fiber
import cats.effect.kernel.Poll
import cats.effect.kernel.Ref
import scala.concurrent.duration.FiniteDuration

class ConcurrentEffect[F[_] : Sync : Concurrent] extends Concurrent[F] with Sync[F] {

  override def suspend[A](hint: Sync.Type)(thunk: => A): F[A] =
    Sync[F].suspend(hint)(thunk)

  override def monotonic: F[FiniteDuration] =
    Sync[F].monotonic

  override def realTime: F[FiniteDuration] =
    Sync[F].realTime

  override def ref[A](a: A): F[Ref[F, A]] =
    Concurrent[F].ref(a)

  override def deferred[A]: F[Deferred[F, A]] =
    Concurrent[F].deferred[A]

  override def start[A](fa: F[A]): F[Fiber[F, Throwable, A]] =
    Concurrent[F].start(fa)

  override def never[A]: F[A] =
    Concurrent[F].never[A]

  override def cede: F[Unit] =
    Concurrent[F].cede

  override def forceR[A, B](fa: F[A])(fb: F[B]): F[B] =
    Concurrent[F].forceR[A, B](fa)(fb)

  override def uncancelable[A](body: Poll[F] => F[A]): F[A] =
    Concurrent[F].uncancelable[A](body)

  override def canceled: F[Unit] =
    Concurrent[F].canceled

  override def onCancel[A](fa: F[A], fin: F[Unit]): F[A] =
    Concurrent[F].onCancel[A](fa, fin)

  override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] =
    Concurrent[F].flatMap[A, B](fa)(f)

  override def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] =
    Concurrent[F].tailRecM[A, B](a)(f)

  override def raiseError[A](e: Throwable): F[A] =
    Concurrent[F].raiseError[A](e)

  override def handleErrorWith[A](fa: F[A])(f: Throwable => F[A]): F[A] =
    Concurrent[F].handleErrorWith[A](fa)(f)

  override def pure[A](x: A): F[A] =
    Concurrent[F].pure[A](x)
}

object ConcurrentEffect {

  def apply[F[_] : Concurrent : Sync] = new ConcurrentEffect[F]

  implicit def concurrentEffect[F[_] : Concurrent : Sync] = ConcurrentEffect[F]
}