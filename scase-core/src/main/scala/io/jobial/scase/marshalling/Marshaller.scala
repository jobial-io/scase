package io.jobial.scase.marshalling

import java.io.OutputStream

import cats.effect.IO

trait Marshaller[M] {
  def marshal(o: M): Array[Byte]

  def marshal(o: M, out: OutputStream): IO[OutputStream]

  def marshalToText(o: M): String
}

object Marshaller {

  def apply[M: Marshaller] = implicitly[Marshaller[M]]
}