package io.jobial.scase.monix

import monix.eval.TaskLift
import monix.reactive.Observable

trait SwitchMapObservable[A] {

  def observable: Observable[A]

  def flatMap[B](f: A => SwitchMapObservable[B]): SwitchMapObservable[B] =
    new DelegatingSwitchMapObservable(observable.switchMap(f.andThen(_.observable)))

  def map[B](f: A => B): SwitchMapObservable[B] =
    new DelegatingSwitchMapObservable(observable.map(f))
  
  def filter(p: A => Boolean): SwitchMapObservable[A] = observable.filter(p)

  def to[F[_] : TaskLift]: F[Unit] = observable.completedL.to[F]
}

class DelegatingSwitchMapObservable[A](val observable: Observable[A]) extends SwitchMapObservable[A]

