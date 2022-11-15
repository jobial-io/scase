package io.jobial.scase.marshalling

package object serialization extends SerializationMarshallingInstances {

  implicit def serializationMarshalling[T] = new SerializationMarshalling[T]
}