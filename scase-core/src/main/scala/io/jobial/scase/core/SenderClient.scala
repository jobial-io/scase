package io.jobial.scase.core


case class SendMessageContext(
  attributes: Map[String, String] = Map()
)

trait SenderClient[F[_], REQ] {

  def send[REQUEST <: REQ](request: REQUEST)
    (implicit sendMessageContext: SendMessageContext = SendMessageContext()): F[MessageSendResult[F, REQUEST]]

  def ![REQUEST <: REQ](request: REQUEST)
    (implicit sendMessageContext: SendMessageContext = SendMessageContext()) =
    send(request)

  def stop: F[Unit]
}
