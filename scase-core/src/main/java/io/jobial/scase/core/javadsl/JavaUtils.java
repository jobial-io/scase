package io.jobial.scase.core.javadsl;

import cats.effect.*;
import io.jobial.scase.util.Hash$;
import scala.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContext$;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class JavaUtils {

    public static <T> CompletableFuture<T> scalaFutureToCompletableFuture(Future<T> f) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.scalaFutureToCompletableFuture(f, executionContext);
    }

    public static <T> CompletableFuture<T> ioToCompletableFuture(IO<T> io) {
        return scalaFutureToCompletableFuture(io.unsafeToFuture());
    }

    public static <T> IO<T> completableFutureToIO(final CompletableFuture<T> f) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.completableFutureToIO(f, contextShift);
    }

    private static <T> Future<T> completableFutureToScalaFuture(CompletableFuture<T> f) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.completableFutureToScalaFuture(f);
    }

    public static <T> Option<T> javaOptionalToScala(Optional<T> o) {
        if (o.isPresent()) {
            return Some$.MODULE$.apply(o.get());
        } else {
            return Option$.MODULE$.<T>empty();
        }
    }
    
    public static scala.concurrent.duration.FiniteDuration javaDurationToScala(Duration duration) {
        return scala.concurrent.duration.Duration.fromNanos(duration.toNanos());
    }

    public static <A, B> scala.collection.immutable.Map<A, B> javaMapToScala(Map<A, B> map) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.javaMapToScala(map);
    }

    public static <A, B> Function1<A, B> javaFunctionToScala(Function<A, B> f) {
        return package$.MODULE$.javaFunctionToScala(f);
    }

    public static Function0<BoxedUnit> javaRunnableToScala(Runnable f) {
        return package$.MODULE$.javaRunnableToScala(f);
    }

    public static String uuid(int length) {
        return Hash$.MODULE$.uuid(length, 36);
    }

    public static CompletableFuture<Service> service(Object service) {
        return ioToCompletableFuture((IO<io.jobial.scase.core.Service<IO>>) service)
                .thenApply(r -> new Service(r));
    }

    // Java cannot figure out effectful types correctly... 
    public static <M> CompletableFuture<ReceiverClient<M>> receiverClient(Object client) {
        return ioToCompletableFuture((IO<io.jobial.scase.core.ReceiverClient<IO, M>>) client)
                .thenApply(r -> new ReceiverClient(r));
    }

    public static <REQ, RESP> CompletableFuture<RequestResponseClient<REQ, RESP>> requestResponseClient(Object client) {
        return ioToCompletableFuture((IO<io.jobial.scase.core.RequestResponseClient<IO, REQ, RESP>>) client)
                .thenApply(r -> new RequestResponseClient(r));
    }

    public static <REQ> CompletableFuture<SenderClient<REQ>> senderClient(Object client) {
        return ioToCompletableFuture((IO<io.jobial.scase.core.SenderClient<IO, REQ>>) client)
                .thenApply(r -> new SenderClient(r));
    }

    public static ExecutionContext executionContext = ExecutionContext$.MODULE$.global();

    public static ContextShift<IO> contextShift = IO.contextShift(executionContext);

    public static Concurrent<IO> concurrent = IO$.MODULE$.ioConcurrentEffect(contextShift);

    public static Timer<IO> timer = IO$.MODULE$.timer(executionContext);


}
