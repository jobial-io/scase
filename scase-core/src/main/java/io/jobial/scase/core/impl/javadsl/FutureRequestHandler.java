package io.jobial.scase.core.impl.javadsl;

import cats.MonadError;
import cats.effect.IO;
import io.jobial.scase.core.RequestContext;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.RequestResponseMapping;
import scala.Function1;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.completableFutureToIO;
import static io.jobial.scase.core.javadsl.Defaults.defaultSendMessageContext;

public interface FutureRequestHandler<REQ, RESP> extends RequestHandler<IO, REQ, RESP> {
    
    @Override
    default Function1<REQ, IO> handleRequestOrFail(RequestContext<IO> context, MonadError<IO, Throwable> me) {
        return RequestHandler.super.handleRequestOrFail(context, me);
    }

    @Override
    default UnknownRequest$ UnknownRequest() {
        return new UnknownRequest$();
    }

    default Function1<REQ, IO> handleRequest(RequestContext<IO> context) {
        return request -> completableFutureToIO(handleRequest(request).thenApply(response ->
                context.reply(request, response, new RequestResponseMapping<REQ, RESP>() {
                }, defaultSendMessageContext)));
    }

    CompletableFuture<RESP> handleRequest(REQ request);
}
