package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.ReceiverClient;
import io.jobial.scase.core.javadsl.SenderClient;
import io.jobial.scase.core.javadsl.Service;
import scala.util.Either;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.ioAsyncEffect;

public class PulsarStreamServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarStreamServiceConfiguration config;

    PulsarStreamServiceConfiguration(io.jobial.scase.pulsar.PulsarStreamServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public CompletableFuture<Service> service(RequestHandler<IO, REQ, RESP> requestHandler, PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(requestHandler, ioAsyncEffect, pulsarContext.getContext()));
    }

    public CompletableFuture<Service> service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return service(requestHandler, new PulsarContext());
    }

    public CompletableFuture<SenderClient<REQ>> senderClient(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.senderClient(config.senderClient(ioAsyncEffect, pulsarContext.getContext()));
    }

    public CompletableFuture<SenderClient<REQ>> senderClient() throws ExecutionException, InterruptedException {
        return senderClient(new PulsarContext());
    }

    public CompletableFuture<ReceiverClient<Either<Throwable, RESP>>> receiverClient(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.<Either<Throwable, RESP>>receiverClient(config.receiverClient(ioAsyncEffect, pulsarContext.getContext()));
    }

    public CompletableFuture<ReceiverClient<Either<Throwable, RESP>>> receiverClient() throws ExecutionException, InterruptedException {
        return receiverClient(new PulsarContext());
    }
}
