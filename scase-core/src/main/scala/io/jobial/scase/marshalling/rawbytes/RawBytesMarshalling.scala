package io.jobial.scase.marshalling.rawbytes

import cats.effect.Concurrent
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

trait RawBytesMarshalling extends CatsUtils {

  implicit val rawBytesMarshaller = new Marshaller[Array[Byte]] {
    def marshal(o: Array[Byte]): Array[Byte] = o

    def marshal[F[_] : Concurrent](o: Array[Byte], out: OutputStream) = delay {
      out.write(o)
      out
    }

    def marshalToText(o: Array[Byte]) =
      Base64.getEncoder.encodeToString(marshal(o))
  }

  implicit val rawBytesUnmarshaller = new Unmarshaller[Array[Byte]] {

    def unmarshal(bytes: Array[Byte]) = Right(bytes)

    def unmarshal[F[_] : Concurrent](in: InputStream) =
      delay(IOUtils.toByteArray(in))

    def unmarshalFromText(text: String) =
      unmarshal(Base64.getDecoder.decode(text))
  }
}
