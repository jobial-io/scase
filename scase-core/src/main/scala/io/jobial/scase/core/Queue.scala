package io.jobial.scase.core

trait Queue[M] extends MessageConsumer[M] with MessageProducer[M]
