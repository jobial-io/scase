package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.ReceiverClient;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarMessageSourceServiceConfiguration<M> {

    io.jobial.scase.pulsar.PulsarMessageSourceServiceConfiguration config;

    PulsarMessageSourceServiceConfiguration(io.jobial.scase.pulsar.PulsarMessageSourceServiceConfiguration<M> config) {
        this.config = config;
    }

    public ReceiverClient<M> client() throws ExecutionException, InterruptedException {
        return JavaUtils.<M>receiverClient(config.client(concurrent, timer, new PulsarContext().getContext(), contextShift)).get();
    }
}
