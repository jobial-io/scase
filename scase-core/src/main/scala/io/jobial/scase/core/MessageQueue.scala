package io.jobial.scase.core

trait MessageQueue[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M]
