package io.jobial.scase.marshalling

import java.io.InputStream
import java.io.OutputStream

trait Marshaller[M] {
  def marshal(o: M): Array[Byte]

  def marshal(o: M, out: OutputStream): Unit

  def marshalToText(o: M): String
}

trait Unmarshaller[M] {
  def unmarshal(bytes: Array[Byte]): M

  def unmarshal(in: InputStream): M

  def unmarshalFromText(text: String): M
}
