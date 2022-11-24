package io.jobial.scase.core.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestResponseMapping;
import io.jobial.scase.core.RequestResponseResult;
import io.jobial.scase.core.RequestTimeout;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.javadsl.JavaUtils.ioToCompletableFuture;

public class RequestResponseClient<REQ, RESP> {

    private io.jobial.scase.core.RequestResponseClient<IO, REQ, RESP> client;

    public RequestResponseClient(io.jobial.scase.core.RequestResponseClient<IO, REQ, RESP> client) {
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

    public CompletableFuture<?> stop() {
        return ioToCompletableFuture(client.stop());
    }
}
