package io.jobial.scase.core

trait SenderClient[F[_], REQ] {

  def send[REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext): F[MessageSendResult[F, REQUEST]]

  def ![REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext) =
    send(request)
}
