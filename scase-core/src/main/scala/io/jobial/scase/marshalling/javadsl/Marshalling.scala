package io.jobial.scase.marshalling.javadsl

import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

trait Marshalling[M] {

  def marshaller: Marshaller[M]

  def unmarshaller: Unmarshaller[M]

  def eitherMarshaller: Marshaller[Either[Throwable, M]]

  def eitherUnmarshaller: Unmarshaller[Either[Throwable, M]]

  def throwableMarshaller: Marshaller[Throwable]

  def throwableUnmarshaller: Unmarshaller[Throwable]
}
