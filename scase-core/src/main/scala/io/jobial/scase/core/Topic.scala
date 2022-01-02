package io.jobial.scase.core

// TODO: revisit this
trait Topic[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M]
