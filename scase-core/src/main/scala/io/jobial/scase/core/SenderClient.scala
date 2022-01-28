package io.jobial.scase.core

// TODO: rename to OneWayClient? 
trait SenderClient[F[_], REQ] {

  def send[REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext = SendRequestContext()): F[MessageSendResult[F, REQUEST]]

  def ![REQUEST <: REQ](request: REQUEST)
    (implicit sendRequestContext: SendRequestContext = SendRequestContext()) =
    send(request)
}
