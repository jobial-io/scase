package io.jobial.scase.marshalling
import cats.effect.IO

import java.io.{InputStream, OutputStream}

trait DefaultMarshallers {

  implicit def eitherMarshaller[A: Marshaller, B: Marshaller]: Marshaller[Either[A, B]] =
    new Marshaller[Either[A, B]] {
      override def marshal(o: Either[A, B]): Array[Byte] = ???

      override def marshal(o: Either[A, B], out: OutputStream): IO[OutputStream] = ???

      override def marshalToText(o: Either[A, B]): String = ???
    }
    
  implicit def eitherUnmarshaller[A: Unmarshaller, B: Unmarshaller]: Unmarshaller[Either[A, B]] =
    new Unmarshaller[Either[A, B]] {
      override def unmarshal(bytes: Array[Byte]): Either[Throwable, Either[A, B]] = ???

      override def unmarshal(in: InputStream): IO[Either[A, B]] = ???

      override def unmarshalFromText(text: String): Either[Throwable, Either[A, B]] = ???
    }
}
