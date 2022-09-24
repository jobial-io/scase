package io.jobial.scase.core.impl.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestResponseMapping;
import io.jobial.scase.core.RequestResponseResult;
import io.jobial.scase.core.RequestTimeout;
import io.jobial.scase.core.javadsl.SendRequestContext;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.ioToCompletableFuture;

public class ConsumerProducerRequestResponseClient<REQ, RESP> {

    private io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient<IO, REQ, RESP> client;

    public ConsumerProducerRequestResponseClient(io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient<IO, REQ, RESP> client) {
        this.client = client;
    }

    private RequestResponseMapping<REQ, RESP> requestResponseMapping = new RequestResponseMapping<REQ, RESP>() {
    };

    public CompletableFuture<RESP> sendRequest(REQ request, SendRequestContext sendRequestContext) throws RequestTimeout {
        return ioToCompletableFuture(client.sendRequestWithResponseMapping(request, requestResponseMapping, sendRequestContext.getContext()).flatMap(
                JavaUtils.<RequestResponseResult<IO, REQ, RESP>, IO<RESP>>javaFunctionToScala(r -> r.response().message())
        ));
    }

    public CompletableFuture<RESP> sendRequest(REQ request) throws RequestTimeout {
        return sendRequest(request, new SendRequestContext());
    }

    public CompletableFuture<RESP> sendRequestWithFullResult(REQ request) throws RequestTimeout {
        return sendRequest(request, new SendRequestContext());
    }
}
