package io.jobial.scase.monix


import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.MVar
import cats.effect.concurrent.MVar2
import cats.effect.concurrent.Ref
import cats.implicits._
import monix.eval.TaskLike
import monix.reactive.Observable

class Source[F[_] : Monad : TaskLike, A](value: MVar2[F, A], last: Ref[F, Option[A]])
  extends SwitchMapObservable[A] {

  def push(a: A) =
    for {
      _ <- last.update(_ => Some(a))
      _ <- value.put(a)
    } yield ()

  val observable =
    Observable.concat(
      Observable.from {
        for {
          v <- value.tryTake
          l <- last.get
        } yield v.orElse(l) match {
          case Some(a) =>
            Observable(a)
          case None =>
            Observable.empty
        }
      }.flatten,
      Observable.repeatEvalF(value.take)
    )
}

object Source {

  def apply[F[_] : Concurrent : TaskLike, A] =
    for {
      value <- MVar.empty[F, A]
      last <- Ref.of[F, Option[A]](None)
    } yield new Source(value, last)
}