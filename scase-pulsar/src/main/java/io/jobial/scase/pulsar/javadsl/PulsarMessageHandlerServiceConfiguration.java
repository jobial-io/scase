package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.MessageHandler;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.ReceiverClient;
import io.jobial.scase.core.javadsl.SenderClient;
import io.jobial.scase.core.javadsl.Service;
import scala.util.Either;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.concurrent;
import static io.jobial.scase.core.javadsl.JavaUtils.timer;

public class PulsarMessageHandlerServiceConfiguration<M> {

    io.jobial.scase.pulsar.PulsarMessageHandlerServiceConfiguration config;

    PulsarMessageHandlerServiceConfiguration(io.jobial.scase.pulsar.PulsarMessageHandlerServiceConfiguration<M> config) {
        this.config = config;
    }

    public Service service(MessageHandler<IO, M> messageHandler, PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(messageHandler, concurrent, timer, pulsarContext.getContext())).get();
    }

    public Service service(MessageHandler<IO, M> messageHandler) throws ExecutionException, InterruptedException {
        return service(messageHandler, new PulsarContext());
    }

    public SenderClient<M> client(PulsarContext pulsarContext) throws ExecutionException, InterruptedException {
        return JavaUtils.<M>senderClient(config.client(concurrent, timer, pulsarContext.getContext())).get();
    }

    public SenderClient<M> client() throws ExecutionException, InterruptedException {
        return client(new PulsarContext());
    }
}
