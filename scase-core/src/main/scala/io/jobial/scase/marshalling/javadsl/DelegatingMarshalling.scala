package io.jobial.scase.marshalling.javadsl

trait DelegatingMarshalling[M] extends Marshalling[M] {

  def delegate[M]: Marshalling[M]

  def eitherMarshaller = delegate[Either[Throwable, M]].marshaller

  def eitherUnmarshaller = delegate[Either[Throwable, M]].unmarshaller

  def throwableMarshaller = delegate[Throwable].marshaller

  def throwableUnmarshaller = delegate[Throwable].unmarshaller
}
