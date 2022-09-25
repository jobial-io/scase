package io.jobial.scase.pulsar.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.javadsl.JavaUtils;
import io.jobial.scase.core.javadsl.ReceiverClient;
import io.jobial.scase.core.javadsl.SenderClient;
import io.jobial.scase.core.javadsl.Service;
import scala.util.Either;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public class PulsarStreamServiceConfiguration<REQ, RESP> {

    io.jobial.scase.pulsar.PulsarStreamServiceConfiguration config;

    PulsarStreamServiceConfiguration(io.jobial.scase.pulsar.PulsarStreamServiceConfiguration<REQ, RESP> config) {
        this.config = config;
    }

    public Service service(RequestHandler<IO, REQ, RESP> requestHandler) throws ExecutionException, InterruptedException {
        return JavaUtils.service(config.service(requestHandler, concurrent, timer, new PulsarContext().getContext())).get();
    }

    public SenderClient<REQ> senderClient() throws ExecutionException, InterruptedException {
        return JavaUtils.<REQ>senderClient(config.senderClient(concurrent, timer, new PulsarContext().getContext())).get();
    }

    public ReceiverClient<Either<Throwable, RESP>> receiverClient() throws ExecutionException, InterruptedException {
        return JavaUtils.<Either<Throwable, RESP>>receiverClient(config.receiverClient(concurrent, timer, new PulsarContext().getContext())).get();
    }
}
