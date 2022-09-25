package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.ReceiverClient;
import io.jobial.scase.core.javadsl.SenderClient;
import io.jobial.scase.core.javadsl.Service;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarStreamServiceWithErrorTopicConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarStreamServiceWithErrorTopicConfiguration config;

    PulsarStreamServiceWithErrorTopicConfiguration(io.jobial.scase.pulsar.PulsarStreamServiceWithErrorTopicConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(requestHandler, concurrent, new PulsarContext().getContext(), contextShift)).get();
    }

    public SenderClient<REQ> senderClient() throws ExecutionException, InterruptedException {
        return JavaUtils.<REQ>senderClient(config.senderClient(concurrent, timer, new PulsarContext().getContext(), contextShift)).get();
    }

    public ReceiverClient<RESP> responseReceiverClient() throws ExecutionException, InterruptedException {
        return JavaUtils.<RESP>receiverClient(config.responseReceiverClient(concurrent, timer, new PulsarContext().getContext(), contextShift)).get();
    }

    public ReceiverClient<Throwable> errorReceiverClient() throws ExecutionException, InterruptedException {
        return JavaUtils.<Throwable>receiverClient(config.errorReceiverClient(concurrent, timer, new PulsarContext().getContext(), contextShift)).get();
    }
}
