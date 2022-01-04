package io.jobial.scase.core


trait MessageContext[F[_]] {
}

trait MessageHandler[F[_], M] {

  type Handler = Function[M, F[Unit]]

  def handle(implicit context: MessageContext[F]): Handler
}
