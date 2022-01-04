package io.jobial.scase.core

import scala.annotation.implicitNotFound

/**
 * Type class to bind a request message type to a response type.
 * It makes request-response mapping pluggable, without enforcing any kind of convention on the implementor.
 * 
 * @tparam REQUEST
 * @tparam RESPONSE
 */
@implicitNotFound("No mapping found from request type ${REQUEST} to response type ${RESPONSE}")
trait RequestResponseMapping[REQUEST, RESPONSE]
