package io.jobial.scase.marshalling.serialization

import io.jobial.scase.marshalling._
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import scala.util.Try
import io.jobial.scase.util._

trait SerializationMarshalling {

  // TODO: make gzip optional

  implicit def javaSerializationWithGzipObjectMarshaller[T] = new BinaryFormatMarshaller[T] {

    def marshalToOutputStream(o: T, out: OutputStream) = {
      val oos = new ObjectOutputStream(new GZIPOutputStream(out))
      oos.writeObject(o)
      oos.close
    }
  }

  implicit def javaSerializationWithGzipObjectUnmarshaller[T] = new BinaryFormatUnmarshaller[T] {
    def unmarshalFromInputStream(in: InputStream) = Try {
      val ois = new ObjectInputStream(new GZIPInputStream(in))
      ois.readObject.asInstanceOf[T]
    }.toEither
  }

}
