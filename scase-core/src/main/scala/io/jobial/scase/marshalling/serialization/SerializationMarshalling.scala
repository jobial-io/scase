package io.jobial.scase.marshalling.serialization

import cats.effect.IO
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, ObjectInputStream, ObjectOutputStream, OutputStream}
import java.util.Base64
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Try

trait SerializationMarshalling {
  
  // TODO: make gzip optional

  implicit def javaSerializationWithGzipObjectMarshaller[T] = new Marshaller[T] {
    def marshal(o: T): Array[Byte] = {
      val b = new ByteArrayOutputStream(256)
      marshalToOutputStream(o, b)
      b.close
      b.toByteArray
    }

    def marshal(o: T, out: OutputStream) =
      IO(marshalToOutputStream(o, out))

    private def marshalToOutputStream(o: T, out: OutputStream) = {
      val oos = new ObjectOutputStream(new GZIPOutputStream(out))
      oos.writeObject(o)
      oos.close
      oos
    }

    def marshalToText(o: T) =
      Base64.getEncoder.encodeToString(marshal(o))
  }

  implicit def javaSerializationWithGzipObjectUnmarshaller[T] = new Unmarshaller[T] {
    def unmarshal(bytes: Array[Byte]) =
      unmarshalFromInputStream(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream) =
      IO.fromEither(unmarshalFromInputStream(in))

    private def unmarshalFromInputStream(in: InputStream) = Try {
      val ois = new ObjectInputStream(new GZIPInputStream(in))
      ois.readObject.asInstanceOf[T]
    }.toEither

    def unmarshalFromText(text: String) =
      unmarshal(Base64.getDecoder.decode(text))
  }

}
