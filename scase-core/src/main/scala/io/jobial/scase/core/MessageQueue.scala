package io.jobial.scase.core

// TODO: do we need this?
trait MessageQueue[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M]
