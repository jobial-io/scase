package io.jobial.scase.core.impl.javadsl;

import cats.effect.IO;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.ioToCompletableFuture;

public class ConsumerProducerRequestResponseService<REQ, RESP> {

    private io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP> service;

    public ConsumerProducerRequestResponseService(io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP> service) {
        this.service = service;
    }
    
    public CompletableFuture<ServiceState> start() {
        return ioToCompletableFuture(service.start())
                .thenApply(r -> new ServiceState((io.jobial.scase.core.ServiceState<IO>) r));
    }
}
