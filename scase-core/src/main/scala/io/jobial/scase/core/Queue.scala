package io.jobial.scase.core

trait Queue[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M]
