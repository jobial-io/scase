package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.Service;
import io.jobial.scase.core.javadsl.SenderClient;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarStreamServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarStreamServiceConfiguration config;

    PulsarStreamServiceConfiguration(io.jobial.scase.pulsar.PulsarStreamServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP>>) config.service(requestHandler, concurrent, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new Service(r))
                .get();
    }

    public SenderClient<REQ> client() throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ProducerSenderClient<IO, REQ>>) config.client(concurrent, timer, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new SenderClient(r))
                .get();
    }
}
