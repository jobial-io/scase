package io.jobial.scase.core

trait Topic[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M]
