package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.impl.javadsl.ConsumerProducerRequestResponseService;
import io.jobial.scase.core.impl.javadsl.SenderClient;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.*;

public class PulsarStreamServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarStreamServiceConfiguration config;

    PulsarStreamServiceConfiguration(io.jobial.scase.pulsar.PulsarStreamServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public ConsumerProducerRequestResponseService<REQ, RESP> service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP>>) config.service(requestHandler, concurrent, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new ConsumerProducerRequestResponseService(r))
                .get();
    }

    public SenderClient<REQ> client() throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ProducerSenderClient<IO, REQ>>) config.client(concurrent, timer, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new SenderClient(r))
                .get();
    }
}