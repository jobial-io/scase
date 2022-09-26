package io.jobial.scase.marshalling

import cats.effect.Concurrent
import cats.effect.IO
import io.jobial.scase.core.impl.CatsUtils
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64

trait BinaryFormatMarshaller[M] extends Marshaller[M] with CatsUtils {

  def marshal(o: M): Array[Byte] = {
    val b = new ByteArrayOutputStream(256)
    marshalToOutputStream(o, b)
    b.close
    b.toByteArray
  }

  def marshal[F[_] : Concurrent](o: M, out: OutputStream) =
    delay(marshalToOutputStream(o, out))

  protected def marshalToOutputStream(o: M, out: OutputStream)

  def marshalToText(o: M) =
    Base64.getEncoder.encodeToString(marshal(o))

}
