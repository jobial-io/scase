package io.jobial.scase.marshalling

import cats.effect.IO
import org.apache.commons.io.IOUtils
import spray.json._

import java.io._
import java.util.Base64

package object sprayjson {

  implicit def sprayJsonMarshaller[T: JsonWriter] = new Marshaller[T] {
    def marshal(o: T): Array[Byte] = {
      val b = new ByteArrayOutputStream(8192)
      marshalToOutputStream(o, b)
      b.close
      b.toByteArray
    }

    def marshal(o: T, out: OutputStream) =
      IO(marshalToOutputStream(o, out))

    private def marshalToOutputStream(o: T, out: OutputStream) = {
      val ps = new PrintStream(out, false, "utf-8")
      ps.print(o.toJson.compactPrint)
      ps.close
      ps
    }

    def marshalToText(o: T) =
      Base64.getEncoder.encodeToString(marshal(o))
  }

  implicit def sprayJsonUnmarshaller[T: JsonReader] = new Unmarshaller[T] {
    def unmarshal(bytes: Array[Byte]) =
      unmarshalFromInputStream(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream) =
      IO(unmarshalFromInputStream(in))

    private def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, "utf-8"))

    def unmarshalFromText(text: String) =
      text.parseJson.convertTo[T]
  }

}
