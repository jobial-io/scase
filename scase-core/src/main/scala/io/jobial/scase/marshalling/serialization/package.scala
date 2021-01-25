package io.jobial.scase.marshalling

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, ObjectInputStream, ObjectOutputStream, OutputStream}
import java.util.Base64
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

package object serialization {

  implicit def javaSerializationWithGzipObjectMarshallable[T] = new Marshallable[T] {
    def marshal(o: T): Array[Byte] = {
      val b = new ByteArrayOutputStream(8192)
      marshal(o, b)
      b.close
      b.toByteArray
    }

    def marshal(o: T, out: OutputStream) = try {
      val oos = new ObjectOutputStream(new GZIPOutputStream(out))
      oos.writeObject(o)
      oos.close
    } catch {
      case x: Throwable =>
        println(o)
        x.printStackTrace
    }

    def unmarshal(bytes: Array[Byte]) =
      unmarshal(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream): T = {
      val ois = new ObjectInputStream(new GZIPInputStream(in))
      ois.readObject.asInstanceOf[T]
    }

    def marshalToText(o: T) =
      Base64.getEncoder.encodeToString(marshal(o))

    def unmarshalFromText(text: String) =
      unmarshal(Base64.getDecoder.decode(text))
  }
}
