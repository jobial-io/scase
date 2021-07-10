//package io.jobial.scase.local
//
//import cats.effect.Concurrent
//import io.jobial.scase.core.{RequestProcessor, RequestResponseClient, RequestResponseMapping, RequestResponseServiceConfiguration, RequestResult, SendRequestContext}
//import io.jobial.scase.logging.Logging
//
//import scala.concurrent.Await.result
//import scala.concurrent.duration._
//
//
///**
// * Service definition that wraps another service definition: if the definition ever creates a service, it memoizes
// * the request processor. The client created by this service definition uses a local request-response client internally
// * for subsequent requests. If no service is created through this service definition the client will just use the client 
// * from the underlying service definition. This way it is possible to send requests locally in an optimized way if
// * both the client and the server run in the same process, while still avoiding any dependencies in the client code
// * on the server implementations. 
// */
//case class LocalOrRemoteServiceConfiguration[F[_], REQ, RESP](
//  serviceDefinition: RequestResponseServiceConfiguration[F, REQ, RESP],
//  localOnly: Boolean = false
//)(
//  implicit c: Concurrent[F]
//  //implicit monitoringPublisher: MonitoringPublisher
//) extends RequestResponseServiceConfiguration[F, REQ, RESP] with Logging {
//
//  def serviceName = serviceDefinition.serviceName
//
//  val localRequestResponseServiceDefinition = LocalRequestResponseServiceConfiguration[F, REQ, RESP](
//    serviceName
//  )
//
//  private def getOrCreateLocalClient =
//    LocalOrRemoteServiceConfiguration.localClients.get(serviceDefinition, { _ =>
//      logger.info(s"creating local client for service definition $serviceDefinition")
//      localRequestResponseServiceDefinition.client
//    })
//
//  private def getLocalClient =
//    LocalOrRemoteServiceConfiguration.localClients.get(serviceDefinition)
//
//  def service(requestProcessor: RequestProcessor[F, REQ, RESP]) = {
//    if (localOnly) {
//      logger.info(s"started local only service for $requestProcessor and service definition $serviceDefinition")
//      // TODO: this is not atomic
//      val service = localRequestResponseServiceDefinition.service(requestProcessor)
//      getOrCreateLocalClient
//      service
//    } else {
//      val service = serviceDefinition.service(requestProcessor)
//      // TODO: this shouldn't be started here
//      result(localRequestResponseServiceDefinition.service(requestProcessor).startService, 1.minute)
//      getOrCreateLocalClient
//      logger.info(s"started local and remote service for $requestProcessor and service definition $serviceDefinition")
//      service
//    }
//  }
//
//  lazy val client = createClient
//
//  case class LocalOrRemoteRequestResponseClient[F[_], REQ, RESP](serviceDefinition: RequestResponseServiceConfiguration[F, REQ, RESP]) extends RequestResponseClient[F, REQ, RESP] {
//
//    //lazy val remoteClient = serviceDefinition.client
//
//    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
//      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE] =
//      (if (localOnly) Some(getOrCreateLocalClient) else getLocalClient) match {
//        case Some(client: RequestResponseClient[F, REQ, RESP]) =>
//          logger.debug(s"selecting local client for request ${request.toString.take(500)} and service definition $serviceDefinition")
//          client.sendRequest(request)
//        case None =>
//          //println(LocalOrRemoteRequestResponseServiceDefinition.localClients.toMap)
//          logger.warn(s"selecting remote client for request ${request.toString.take(500)} and service definition $serviceDefinition")
//          remoteClient.sendRequest(request)
//      }
//
//  }
//
//  private def createClient = LocalOrRemoteRequestResponseClient[F, REQ, RESP](serviceDefinition)
//}
//
//object LocalOrRemoteServiceConfiguration {
//  // This works in tests because the service definitions are usually not equal (e.g. because queue names are different)
//  val localClients = Map[RequestResponseServiceConfiguration[_, _, _], RequestResponseClient[_, _, _]]()
//}