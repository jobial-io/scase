package io.jobial.scase.marshalling

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream, PrintStream}
import java.util.Base64
import io.circe.parser.decode

import java.nio.charset.StandardCharsets.UTF_8


package object circe {

  implicit def circeMarshaller[T: Encoder] = new Marshaller[T] {
    def marshal(o: T): Array[Byte] = {
      val b = new ByteArrayOutputStream(256)
      marshalToOutputStream(o, b)
      b.close
      b.toByteArray
    }

    def marshal(o: T, out: OutputStream) =
      IO(marshalToOutputStream(o, out))

    private def marshalToOutputStream(o: T, out: OutputStream) = {
      val ps = new PrintStream(out, false, UTF_8.name)
      ps.print(o.asJson.toString)
      ps.close
      ps
    }

    def marshalToText(o: T) =
      Base64.getEncoder.encodeToString(marshal(o))
  }

  implicit def circeUnmarshaller[T: Decoder] = new Unmarshaller[T] {
    def unmarshal(bytes: Array[Byte]) =
      unmarshalFromInputStream(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream) =
      unmarshalFromInputStream(in)

    private def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, UTF_8))

    def unmarshalFromText(text: String) =
      IO.fromEither(decode[T](text))
  }

}
