package io.jobial.scase.marshalling

import cats.effect.IO

import java.io.{InputStream, OutputStream}

trait DefaultMarshallers {

  implicit def eitherMarshaller[A: Marshaller, B: Marshaller]: Marshaller[Either[A, B]] =
    new Marshaller[Either[A, B]] {
      override def marshal(o: Either[A, B]): Array[Byte] = o match {
        case Left(a) =>
          Marshaller[A].marshal(a)
        case Right(b) =>
          Marshaller[B].marshal(b)
      }

      override def marshal(o: Either[A, B], out: OutputStream): IO[OutputStream] = o match {
        case Left(a) =>
          Marshaller[A].marshal(a, out)
        case Right(b) =>
          Marshaller[B].marshal(b, out)
      }

      override def marshalToText(o: Either[A, B]): String = o match {
        case Left(a) =>
          Marshaller[A].marshalToText(a)
        case Right(b) =>
          Marshaller[B].marshalToText(b)
      }
    }

  implicit def eitherUnmarshaller[A: Unmarshaller, B: Unmarshaller]: Unmarshaller[Either[A, B]] =
    new Unmarshaller[Either[A, B]] {
      override def unmarshal(bytes: Array[Byte]): Either[Throwable, Either[A, B]] =
        Unmarshaller[B].unmarshal(bytes) match {
          case Left(_) =>
            Unmarshaller[A].unmarshal(bytes) match {
              case Left(l) =>
                Left(l)
              case Right(r) =>
                Right[Throwable, Either[A, B]](Left(r))
            }
          case Right(r) =>
            Right[Throwable, Either[A, B]](Right(r))
        }

      override def unmarshal(in: InputStream): IO[Either[A, B]] =
        Unmarshaller[B].unmarshal(in).map(b => Right[A, B](b)).handleErrorWith(_ =>
          Unmarshaller[A].unmarshal(in).map(a => Left[A, B](a)))

      override def unmarshalFromText(text: String): Either[Throwable, Either[A, B]] =
        Unmarshaller[B].unmarshalFromText(text) match {
          case Left(_) =>
            Unmarshaller[A].unmarshalFromText(text) match {
              case Left(l) =>
                Left(l)
              case Right(r) =>
                Right[Throwable, Either[A, B]](Left(r))
            }
          case Right(r) =>
            Right[Throwable, Either[A, B]](Right(r))
        }
    }
}
