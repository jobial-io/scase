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

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler, PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(requestHandler, concurrent, timer, pulsarContext.getContext())).get();
    }

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return service(requestHandler, new PulsarContext());
    }

    public RequestResponseClient<REQ, RESP> client(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.<REQ, RESP>requestResponseClient(config.client(concurrent, timer, pulsarContext.getContext())).get();
    }

    public RequestResponseClient<REQ, RESP> client() throws ExecutionException, InterruptedException {
        return client(new PulsarContext());
    }
}
