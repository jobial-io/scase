package io.jobial.scase.marshalling.sprayjson

import cats.effect.IO
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import org.apache.commons.io.IOUtils
import spray.json._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

trait SprayJsonMarshalling {

  implicit def sprayJsonMarshaller[T: JsonWriter] = new Marshaller[T] {
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
      ps.print(o.toJson.compactPrint)
      ps.close
      ps
    }

    def marshalToText(o: T) =
      o.toJson.compactPrint
  }

  implicit def sprayJsonUnmarshaller[T: JsonReader] = new Unmarshaller[T] {
    def unmarshal(bytes: Array[Byte]) =
      unmarshalFromInputStream(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream) =
      unmarshalFromInputStream(in)

    private def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, UTF_8))

    def unmarshalFromText(text: String) =
      IO(text.parseJson.convertTo[T])
  }
}
