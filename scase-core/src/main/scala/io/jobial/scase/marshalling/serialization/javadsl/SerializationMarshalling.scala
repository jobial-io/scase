package io.jobial.scase.marshalling.serialization.javadsl

import io.jobial.scase.marshalling.javadsl.Marshalling

class SerializationMarshalling[REQ, RESP] extends Marshalling[REQ, RESP] with io.jobial.scase.marshalling.serialization.SerializationMarshalling {
  def requestMarshaller[REQ] = javaSerializationWithGzipObjectMarshaller[REQ]

  def requestUnmarshaller[REQ] = javaSerializationWithGzipObjectUnmarshaller[REQ]

  def responseMarshaller[RESP] = javaSerializationWithGzipObjectMarshaller[RESP]

  def responseUnmarshaller[RESP] = javaSerializationWithGzipObjectUnmarshaller[RESP]

  def responseOrThrowableMarshaller[Either[Throwable, RESP]] = javaSerializationWithGzipObjectMarshaller[Either[Throwable, RESP]]

  def responseOrThrowableUnmarshaller[Either[Throwable, RESP]] = javaSerializationWithGzipObjectUnmarshaller[Either[Throwable, RESP]]
}
