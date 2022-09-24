package io.jobial.scase.core.javadsl;

import cats.effect.IO;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.javadsl.JavaUtils.ioToCompletableFuture;

public class ServiceState {

    private final io.jobial.scase.core.ServiceState<IO> state;

    ServiceState(io.jobial.scase.core.ServiceState state) {
        this.state = state;
    }
    
    public CompletableFuture stop() {
        return ioToCompletableFuture(state.stop());
    }
}
