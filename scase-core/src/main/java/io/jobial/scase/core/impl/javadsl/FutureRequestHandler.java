package io.jobial.scase.core.impl.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestHandler;
import io.jobial.scase.core.RequestResponseMapping;
import io.jobial.scase.core.javadsl.RequestContext;
import io.jobial.scase.core.javadsl.SendMessageContext;
import scala.Function1;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.completableFutureToIO;
import static io.jobial.scase.core.impl.javadsl.JavaUtils.javaFunctionToScala;

public interface FutureRequestHandler<REQ, RESP> extends RequestHandler<IO, REQ, RESP> {

    default Function1<REQ, IO> handleRequest(io.jobial.scase.core.RequestContext<IO> context) {
        return javaFunctionToScala(request -> completableFutureToIO(handleRequest(request, new RequestContext(context)).thenApply(response ->
                context.reply(request, response, new RequestResponseMapping<REQ, RESP>() {
                }, new SendMessageContext().getContext()))));
    }

    CompletableFuture<RESP> handleRequest(REQ request, RequestContext context);

}
