package io.jobial.scase.marshalling

import cats.effect.Concurrent
import io.jobial.scase.core.impl.CatsUtils
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64

trait BinaryFormatUnmarshaller[M] extends Unmarshaller[M] with CatsUtils {
  def unmarshal(bytes: Array[Byte]) =
    unmarshalFromInputStream(new ByteArrayInputStream(bytes))

  def unmarshal[F[_] : Concurrent](in: InputStream) =
    fromEither(unmarshalFromInputStream(in))

  def unmarshalFromInputStream(in: InputStream): Either[Throwable, M]

  def unmarshalFromText(text: String) =
    unmarshal(Base64.getDecoder.decode(text))

}
