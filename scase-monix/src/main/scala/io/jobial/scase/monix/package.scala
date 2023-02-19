package io.jobial.scase

import _root_.monix.eval.TaskLift
import _root_.monix.reactive._
import cats.effect.IO

package object monix {

  implicit class ObservableSyntaxExt[T](observable: Observable[T]) {
    def toSwitchMapObservable: SwitchMapObservable[T] = new DelegatingSwitchMapObservable(observable)
  }

  implicit def toIO[A](o: SwitchMapObservable[A])(implicit T: TaskLift[IO]): IO[Unit] = o.to[IO]

  implicit def toIO[A](o: Observable[A])(implicit T: TaskLift[IO]): IO[Unit] = o.completedL.to[IO]

  implicit def observableToSwitchMapObservable[A](observable: Observable[A]) = observable.toSwitchMapObservable
}