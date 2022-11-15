package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.RequestResponseClient;
import io.jobial.scase.core.javadsl.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.ioAsyncEffect;

public class PulsarRequestResponseServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration config;

    PulsarRequestResponseServiceConfiguration(io.jobial.scase.pulsar.PulsarRequestResponseServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public CompletableFuture<Service> service(RequestHandler<IO, REQ, RESP> requestHandler, PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(requestHandler, ioAsyncEffect, pulsarContext.getContext()));
    }

    public CompletableFuture<Service> service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return service(requestHandler, new PulsarContext());
    }

    public CompletableFuture<RequestResponseClient<REQ, RESP>> client(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.<REQ, RESP>requestResponseClient(config.client(ioAsyncEffect, pulsarContext.getContext()));
    }

    public CompletableFuture<RequestResponseClient<REQ, RESP>> client() throws ExecutionException, InterruptedException {
        return client(new PulsarContext());
    }
}
