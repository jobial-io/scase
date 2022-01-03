package io.jobial.scase.core

/**
 * Configuration for a service. The service definition can be used to create or deploy the service as well as
 * creating a client for it. The service definition can be shared between the server and client side.
 */
trait RequestResponseServiceConfiguration[REQ, RESP] {

  def serviceName: String
}

trait MessageHandlerServiceConfiguration[M] {
}

trait SubscriptionConfiguration[M] {
}
