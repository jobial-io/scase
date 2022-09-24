package io.jobial.scase.core.impl.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.MessageSendResult;
import io.jobial.scase.core.RequestResponseResult;
import io.jobial.scase.core.RequestTimeout;
import io.jobial.scase.core.javadsl.SendMessageContext;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.ioToCompletableFuture;

public class SenderClient<REQ> {

    private io.jobial.scase.core.SenderClient<IO, REQ> client;

    public SenderClient(io.jobial.scase.core.SenderClient<IO, REQ> client) {
        this.client = client;
    }

    public CompletableFuture<?> send(REQ request, SendMessageContext sendMessageContext) throws RequestTimeout {
        return ioToCompletableFuture(client.send(request, sendMessageContext.getContext()));
    }

    public CompletableFuture<?> send(REQ request) throws RequestTimeout {
        return send(request, new SendMessageContext());
    }
}
