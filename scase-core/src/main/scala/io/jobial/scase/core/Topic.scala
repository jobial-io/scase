package io.jobial.scase.core

trait Topic[M] extends MessageConsumer[M] with MessageProducer[M]
