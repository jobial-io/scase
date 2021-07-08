package io.jobial.scase.core

trait SendResult[F[_]] {

  def commit: F[_]
}

trait SenderClient[F[_], REQ] {

  def send[REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext): SendResult[F]

  def ![REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext) =
    send(request)
}
