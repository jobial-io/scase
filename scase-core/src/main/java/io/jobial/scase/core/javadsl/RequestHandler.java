package io.jobial.scase.core.javadsl;

import cats.effect.IO;
import io.jobial.scase.core.RequestResponseMapping;
import scala.Function1;
import scala.util.Right;

import java.util.concurrent.CompletableFuture;

import static io.jobial.scase.core.javadsl.JavaUtils.*;

public interface RequestHandler<REQ, RESP> extends io.jobial.scase.core.RequestHandler<IO, REQ, RESP> {

    default Function1<REQ, IO> handleRequest(io.jobial.scase.core.RequestContext<IO> context) {
        return javaFunctionToScala(request -> completableFutureToIO(handleRequest(request, new RequestContext(context)).thenCompose(response ->
                ioToCompletableFuture(context.reply(request, new Right<Throwable, RESP>(response), new RequestResponseMapping<>() {
                }, new SendMessageContext().getContext())))));
    }

    CompletableFuture<RESP> handleRequest(REQ request, RequestContext context);

}
