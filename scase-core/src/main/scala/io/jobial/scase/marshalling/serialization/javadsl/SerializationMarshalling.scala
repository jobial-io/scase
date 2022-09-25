package io.jobial.scase.marshalling.serialization.javadsl

import io.jobial.scase.marshalling.javadsl.Marshalling

class SerializationMarshalling[M] extends Marshalling[M] with io.jobial.scase.marshalling.serialization.SerializationMarshalling {
  def marshaller[M] = javaSerializationWithGzipObjectMarshaller[M]

  def unmarshaller[M] = javaSerializationWithGzipObjectUnmarshaller[M]

  def eitherMarshaller[Either[Throwable, M]] = javaSerializationWithGzipObjectMarshaller[Either[Throwable, M]]

  def eitherUnmarshaller[Either[Throwable, M]] = javaSerializationWithGzipObjectUnmarshaller[Either[Throwable, M]]
}
