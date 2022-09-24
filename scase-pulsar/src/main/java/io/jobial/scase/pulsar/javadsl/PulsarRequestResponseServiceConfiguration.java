package io.jobial.scase.pulsar.javadsl;

import cats.effect.*;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.impl.javadsl.ConsumerProducerRequestResponseClient;
import io.jobial.scase.core.impl.javadsl.ConsumerProducerRequestResponseService;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.*;
import static io.jobial.scase.pulsar.javadsl.Defaults.defaultPulsarContext;

public class PulsarRequestResponseServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration config;

    PulsarRequestResponseServiceConfiguration(io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public ConsumerProducerRequestResponseService<REQ, RESP> service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP>>) config.service(requestHandler, concurrent, defaultPulsarContext, contextShift))
                .thenApply(r -> new ConsumerProducerRequestResponseService(r))
                .get();
    }

    public ConsumerProducerRequestResponseClient<REQ, RESP> client() throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient<IO, REQ, RESP>>) config.client(concurrent, timer, defaultPulsarContext, contextShift))
                .thenApply(r -> new ConsumerProducerRequestResponseClient(r))
                .get();
    }
}