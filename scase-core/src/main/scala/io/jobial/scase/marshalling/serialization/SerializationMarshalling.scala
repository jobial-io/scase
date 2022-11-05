package io.jobial.scase.marshalling.serialization

import io.jobial.scase.marshalling.DelegatingMarshalling

class SerializationMarshalling[M] extends DelegatingMarshalling[M] with io.jobial.scase.marshalling.serialization.SerializationMarshallingInstances {

  val marshaller = javaSerializationWithGzipObjectMarshaller[M]

  val unmarshaller = javaSerializationWithGzipObjectUnmarshaller[M]

  def delegate[M] = new SerializationMarshalling[M]
}
