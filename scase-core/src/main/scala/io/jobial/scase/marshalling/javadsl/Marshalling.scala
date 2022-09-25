package io.jobial.scase.marshalling.javadsl

import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

trait Marshalling[M] {

  def marshaller[M]: Marshaller[M]

  def unmarshaller[M]: Unmarshaller[M]
  
  def eitherMarshaller[Either[Throwable, M]]: Marshaller[Either[Throwable, M]]

  def eitherUnmarshaller[Either[Throwable, M]]: Unmarshaller[Either[Throwable, M]]
}
