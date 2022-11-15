package io.jobial.scase.core.impl

import cats.implicits._
import io.jobial.scase.core.Service
import io.jobial.scase.core.ServiceState

abstract class DefaultService[F[_]](implicit val concurrent: ConcurrentEffect[F]) extends Service[F] {

  def startAndJoin: F[ServiceState[F]] =
    for {
      state <- start
      result <- state.join
    } yield result
}
