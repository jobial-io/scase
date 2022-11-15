package io.jobial.scase.core.javadsl;

import cats.effect.IO;
import cats.effect.IO$;
import scala.Function1;

import static io.jobial.scase.core.javadsl.JavaUtils.javaFunctionToScala;
import static io.jobial.scase.core.javadsl.JavaUtils.javaRunnableToScala;

public interface MessageHandler<M> extends io.jobial.scase.core.MessageHandler<IO, M> {

    default Function1<M, IO> handleMessage(io.jobial.scase.core.MessageContext<IO> context) {
        return javaFunctionToScala(request -> IO$.MODULE$.delay(javaRunnableToScala(() -> handleMessage(request, new MessageContext(context)))));
    }

    void handleMessage(M request, MessageContext context);

}
