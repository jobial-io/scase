package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.SenderClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.concurrent;
import static io.jobial.scase.core.javadsl.JavaUtils.timer;

public class PulsarMessageDestinationServiceConfiguration<M> {

    io.jobial.scase.pulsar.PulsarMessageDestinationServiceConfiguration config;

    PulsarMessageDestinationServiceConfiguration(io.jobial.scase.pulsar.PulsarMessageDestinationServiceConfiguration<M> config) {
        this.config = config;
    }

    public CompletableFuture<SenderClient<M>> client(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.senderClient(config.client(concurrent, timer, pulsarContext.getContext()));
    }

    public CompletableFuture<SenderClient<M>>  client() throws ExecutionException, InterruptedException {
        return client(new PulsarContext());
    }
}
