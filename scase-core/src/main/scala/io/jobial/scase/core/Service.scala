package io.jobial.scase.core

trait Service[F[_]] {

  def start: F[ServiceState[F]]

  def startAndJoin: F[ServiceState[F]]
}

trait ServiceState[F[_]] {

  def stop: F[ServiceState[F]]

  def join: F[ServiceState[F]]

  def service: Service[F]
}

