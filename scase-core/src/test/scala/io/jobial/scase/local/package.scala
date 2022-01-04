package io.jobial.scase

import cats.effect.{Concurrent, Timer}
import io.jobial.scase.core.RequestHandler

package object local {

  def localServiceAndClient[F[_]: Concurrent: Timer, REQ, RESP](serviceName: String, requestHandler: RequestHandler[F, REQ, RESP]) = 
    LocalRequestResponseServiceConfiguration[REQ, RESP](serviceName).serviceAndClient[F](requestHandler)
}
