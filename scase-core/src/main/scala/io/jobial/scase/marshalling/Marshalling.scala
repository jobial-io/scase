package io.jobial.scase.marshalling

trait Marshalling[M] {

  def marshaller: Marshaller[M]

  def unmarshaller: Unmarshaller[M]

  def eitherMarshaller: Marshaller[Either[Throwable, M]]

  def eitherUnmarshaller: Unmarshaller[Either[Throwable, M]]

  def throwableMarshaller: Marshaller[Throwable]

  def throwableUnmarshaller: Unmarshaller[Throwable]
}

object Marshalling {

  def apply[T: Marshalling] = implicitly[Marshalling[T]]

  implicit def marshaller[M: Marshalling] = Marshalling[M].marshaller

  implicit def unmarshaller[M: Marshalling] = Marshalling[M].unmarshaller

  implicit def eitherMarshaller[M: Marshalling] = Marshalling[M].eitherMarshaller

  implicit def eitherUnmarshaller[M: Marshalling] = Marshalling[M].eitherUnmarshaller

  implicit def throwableMarshaller[M: Marshalling] = Marshalling[M].throwableMarshaller

  implicit def throwableUnmarshaller[M: Marshalling] = Marshalling[M].throwableUnmarshaller
}