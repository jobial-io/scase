package io.jobial.scase.marshalling.javadsl

import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

trait Marshalling[REQ, RESP] {

  def requestMarshaller[REQ]: Marshaller[REQ]

  def requestUnmarshaller[REQ]: Unmarshaller[REQ]

  def responseMarshaller[RESP]: Marshaller[RESP]

  def responseUnmarshaller[RESP]: Unmarshaller[RESP]

  def responseOrThrowableMarshaller[Either[Throwable, RESP]]: Marshaller[Either[Throwable, RESP]]

  def responseOrThrowableUnmarshaller[Either[Throwable, RESP]]: Unmarshaller[Either[Throwable, RESP]]
}
