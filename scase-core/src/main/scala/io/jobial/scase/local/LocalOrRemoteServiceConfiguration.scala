package io.jobial.scase.local

import cats.effect.Concurrent
import io.jobial.scase.core.{RemoteRequestResponseServiceConfiguration, RequestProcessor, RequestResponseClient, RequestResponseMapping, RequestResponseServiceConfiguration, RequestResult, SendRequestContext}
import io.jobial.scase.logging.Logging
import cats.implicits._
import scala.concurrent.duration._

/**
 * This is now just a thin wrapper around the local and remote configs. Not sure if it's worth anymore, needs to be revisited.
 */
case class LocalOrRemoteServiceConfiguration[F[_], REQ, RESP](
  remoteServiceDefinition: RemoteRequestResponseServiceConfiguration[F, REQ, RESP]
)(
  implicit c: Concurrent[F]
  //implicit monitoringPublisher: MonitoringPublisher
) extends RemoteRequestResponseServiceConfiguration[F, REQ, RESP] with Logging {

  def serviceName = remoteServiceDefinition.serviceName

  val localRequestResponseServiceDefinition = LocalRequestResponseServiceConfiguration[REQ, RESP](
    serviceName
  )

  def service(requestProcessor: RequestProcessor[F, REQ, RESP]) =
      remoteServiceDefinition.service(requestProcessor)

  def serviceAndClient(requestProcessor: RequestProcessor[F, REQ, RESP]) = 
    for {
      t <- localRequestResponseServiceDefinition.serviceAndClient(requestProcessor)
      (localService, localClient) = t
      _ <- localService.startService
      remoteService <- remoteServiceDefinition.service(requestProcessor)
    } yield localClient

  def client = remoteServiceDefinition.client
}
