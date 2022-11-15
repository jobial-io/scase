package io.jobial.scase.marshalling

import io.jobial.scase.core.impl.ConcurrentEffect
import java.io.OutputStream

trait Marshaller[M] {
  def marshal(o: M): Array[Byte]

  def marshal[F[_] : ConcurrentEffect](o: M, out: OutputStream): F[Unit]

  def marshalToText(o: M): String
}

object Marshaller {

  def apply[M: Marshaller] = implicitly[Marshaller[M]]
}