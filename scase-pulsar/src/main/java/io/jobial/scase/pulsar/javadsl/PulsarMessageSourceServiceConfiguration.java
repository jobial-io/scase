package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.javadsl.ReceiverClient;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarMessageSourceServiceConfiguration<M> {

    io.jobial.scase.pulsar.PulsarMessageSourceServiceConfiguration config;

    PulsarMessageSourceServiceConfiguration(io.jobial.scase.pulsar.PulsarMessageSourceServiceConfiguration<M> config) {
        this.config = config;
    }

    public ReceiverClient<M> client() throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerReceiverClient<IO, M>>) config.client(concurrent, timer, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new ReceiverClient(r))
                .get();
    }
}
