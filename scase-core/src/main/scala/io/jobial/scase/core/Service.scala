package io.jobial.scase.core

import cats.{Monad, MonadError}
import cats.effect.{Concurrent, IO}
import cats.implicits._

import scala.concurrent.duration._
import scala.util.Try


trait Service[F[_]] {

  def start: F[ServiceState[F]]
  
  def startAndJoin: F[ServiceState[F]]
}

trait ServiceState[F[_]] {

  def stop: F[ServiceState[F]]
  
  def join: F[ServiceState[F]]
}

