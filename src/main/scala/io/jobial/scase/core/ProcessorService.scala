package io.jobial.scase.core

trait ProcessorService[M] {

  def handle(message: M)
}
