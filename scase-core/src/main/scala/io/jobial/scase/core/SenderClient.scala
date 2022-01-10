package io.jobial.scase.core

trait SenderClient[F[_], REQ] {

  def send[REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext = SendRequestContext()): F[MessageSendResult[F, REQUEST]]

  def ![REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext = SendRequestContext()) =
    send(request)
}
