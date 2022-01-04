package io.jobial.scase.core

/**
 * Configuration for a service. The service config can be used to create or deploy a service or to
 * create a client for it. A service configuration can be shared between the server and client side.
 */
trait ServiceConfiguration {

  def serviceName: String
}
