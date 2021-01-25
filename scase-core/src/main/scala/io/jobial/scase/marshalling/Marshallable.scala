package io.jobial.scase.marshalling

import java.io.InputStream
import java.io.OutputStream

trait Marshallable[M] {
  def marshal(o: M): Array[Byte]

  def marshal(o: M, out: OutputStream): Unit

  def marshalToText(o: M): String

  def unmarshal(bytes: Array[Byte]): M
  
  def unmarshal(in: InputStream): M
  
  def unmarshalFromText(text: String): M
}
