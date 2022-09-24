package io.jobial.scase.core.javadsl;

import cats.effect.IO;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.javadsl.JavaUtils.ioToCompletableFuture;

public class Service {

    private io.jobial.scase.core.Service<IO> service;

    public Service(io.jobial.scase.core.Service<IO> service) {
        this.service = service;
    }
    
    public CompletableFuture<ServiceState> start() {
        return ioToCompletableFuture(service.start())
                .thenApply(r -> new ServiceState((io.jobial.scase.core.ServiceState<IO>) r));
    }
}
