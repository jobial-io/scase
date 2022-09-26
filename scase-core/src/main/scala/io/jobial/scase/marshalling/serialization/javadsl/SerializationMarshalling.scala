package io.jobial.scase.marshalling.serialization.javadsl

import io.jobial.scase.marshalling.javadsl.DelegatingMarshalling

class SerializationMarshalling[M] extends DelegatingMarshalling[M] with io.jobial.scase.marshalling.serialization.SerializationMarshalling {

  val marshaller = javaSerializationWithGzipObjectMarshaller[M]

  val unmarshaller = javaSerializationWithGzipObjectUnmarshaller[M]

  def delegate[M] = new SerializationMarshalling[M]
}
