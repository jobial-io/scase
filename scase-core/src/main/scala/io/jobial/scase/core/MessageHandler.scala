package io.jobial.scase.core

trait MessageContext[F[_]] {
  def receiveResult[M](request: M): MessageReceiveResult[F, M]
}

trait MessageHandler[F[_], M] {

  type Handler = Function[M, F[Unit]]

  def handleMessage(implicit context: MessageContext[F]): Handler
}
