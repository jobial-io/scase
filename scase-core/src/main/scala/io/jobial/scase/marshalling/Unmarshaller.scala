package io.jobial.scase.marshalling

import cats.effect.Concurrent
import java.io.InputStream

trait Unmarshaller[M] {
  def unmarshal(bytes: Array[Byte]): Either[Throwable, M]

  def unmarshal[F[_] : Concurrent](in: InputStream): F[M]

  def unmarshalFromText(text: String): Either[Throwable, M]
}

object Unmarshaller {

  def apply[M: Unmarshaller] = implicitly[Unmarshaller[M]]
}