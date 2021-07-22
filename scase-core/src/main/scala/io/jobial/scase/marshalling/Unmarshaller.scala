package io.jobial.scase.marshalling

import java.io.InputStream

import cats.effect.IO

trait Unmarshaller[M] {
  def unmarshal(bytes: Array[Byte]): Either[Throwable, M]

  def unmarshal(in: InputStream): IO[M]

  def unmarshalFromText(text: String): Either[Throwable, M]
}

object Unmarshaller {

  def apply[M: Unmarshaller] = implicitly[Unmarshaller[M]]
}