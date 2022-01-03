package io.jobial.scase.core.impl

import cats.implicits._
import cats.effect.Concurrent
import io.jobial.scase.core.{Service, ServiceState}

abstract class DefaultService[F[_] : Concurrent] extends Service[F] {

  def startAndJoin: F[ServiceState[F]] =
    for {
      state <- start
      result <- state.join
    } yield result
}
