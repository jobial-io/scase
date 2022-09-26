package io.jobial.scase.marshalling

import cats.effect.Concurrent
import java.io.OutputStream
import cats.effect.IO

trait Marshaller[M] {
  def marshal(o: M): Array[Byte]

  def marshal[F[_] : Concurrent](o: M, out: OutputStream): F[Unit]

  def marshalToText(o: M): String
}

object Marshaller {

  def apply[M: Marshaller] = implicitly[Marshaller[M]]
}