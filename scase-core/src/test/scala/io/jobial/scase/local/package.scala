package io.jobial.scase

import cats.effect.Concurrent
import io.jobial.scase.core.RequestProcessor

package object local {

  def localServiceAndClient[F[_]: Concurrent, REQ, RESP](serviceName: String, requestProcessor: RequestProcessor[F, REQ, RESP]) = 
    LocalRequestResponseServiceConfiguration[REQ, RESP](serviceName).serviceAndClient[F](requestProcessor)
}
