package io.jobial.scase.pulsar.javadsl;

import cats.effect.*;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
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
        return JavaUtils.service(config.service(requestHandler, concurrent, new PulsarContext().getContext(), contextShift)).get();
    }

    public RequestResponseClient<REQ, RESP> client() throws ExecutionException, InterruptedException {
        return JavaUtils.<REQ, RESP>requestResponseClient(config.client(concurrent, timer, new PulsarContext().getContext(), contextShift)).get();
    }
}
