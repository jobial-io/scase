package io.jobial.scase.pulsar.javadsl;

import cats.effect.*;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.RequestResponseClient;
import io.jobial.scase.core.javadsl.Service;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarRequestResponseServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration config;

    PulsarRequestResponseServiceConfiguration(io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseService<IO, REQ, RESP>>) config.service(requestHandler, concurrent, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new Service(r))
                .get();
    }

    public RequestResponseClient<REQ, RESP> client() throws ExecutionException, InterruptedException {
        return ioToCompletableFuture((IO<io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient<IO, REQ, RESP>>) config.client(concurrent, timer, new PulsarContext().getContext(), contextShift))
                .thenApply(r -> new RequestResponseClient(r))
                .get();
    }
}
