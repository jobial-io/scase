package io.jobial.scase.marshalling

import java.io.{InputStream, OutputStream}
import java.util.Base64

import cats.effect.IO
import org.apache.commons.io.IOUtils


package object rawbytes {

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

    def unmarshal(bytes: Array[Byte]) = bytes

    def unmarshal(in: InputStream) =
      IO(IOUtils.toByteArray(in))

    def unmarshalFromText(text: String) =
      unmarshal(Base64.getDecoder.decode(text))
  }

}

