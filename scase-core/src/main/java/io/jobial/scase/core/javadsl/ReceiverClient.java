package io.jobial.scase.core.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestTimeout;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.javadsl.JavaUtils.ioToCompletableFuture;

public class ReceiverClient<M> {

    private io.jobial.scase.core.ReceiverClient<IO, M> client;

    public ReceiverClient(io.jobial.scase.core.ReceiverClient<IO, M> client) {
        this.client = client;
    }

    public CompletableFuture<M> receive() throws RequestTimeout {
        return ioToCompletableFuture(client.receive());
    }

    public CompletableFuture<?> stop() {
        return ioToCompletableFuture(client.stop());
    }
}
