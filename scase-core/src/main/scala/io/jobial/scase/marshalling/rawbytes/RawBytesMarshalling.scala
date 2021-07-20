package io.jobial.scase.marshalling.rawbytes

import cats.effect.IO
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import org.apache.commons.io.IOUtils

import java.io.{InputStream, OutputStream}
import java.util.Base64

trait RawBytesMarshalling {
  
  implicit val rawBytesMarshaller = new Marshaller[Array[Byte]] {
    def marshal(o: Array[Byte]): Array[Byte] = o

    def marshal(o: Array[Byte], out: OutputStream) = IO {
      out.write(o)
      out
    }

    def marshalToText(o: Array[Byte]) =
      Base64.getEncoder.encodeToString(marshal(o))
  }

  implicit val rawBytesUnmarshaller = new Unmarshaller[Array[Byte]] {

    def unmarshal(bytes: Array[Byte]) = IO(bytes)

    def unmarshal(in: InputStream) =
      IO(IOUtils.toByteArray(in))

    def unmarshalFromText(text: String) =
      unmarshal(Base64.getDecoder.decode(text))
  }
}
