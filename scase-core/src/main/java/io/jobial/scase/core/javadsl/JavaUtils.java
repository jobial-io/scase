package io.jobial.scase.core.javadsl;

import cats.effect.IO;
import cats.effect.unsafe.IORuntime;
import cats.effect.unsafe.IORuntime$;
import io.jobial.scase.core.impl.AsyncEffect;
import io.jobial.scase.core.impl.ConcurrentEffect;
import io.jobial.scase.core.impl.TemporalEffect;
import io.jobial.scase.util.Hash$;
import scala.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContext$;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class JavaUtils {

    public static <T> CompletableFuture<T> scalaFutureToCompletableFuture(Future<T> f) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.scalaFutureToCompletableFuture(f, executionContext);
    }

    public static <T> CompletableFuture<T> ioToCompletableFuture(IO<T> io) {
        return scalaFutureToCompletableFuture(io.unsafeToFuture(runtime));
    }

    public static <T> IO<T> completableFutureToIO(final CompletableFuture<T> f) {
        return io.jobial.scase.core.javadsl.package$.MODULE$.completableFutureToIO(f);
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

    public static <T> Optional<T> scalaOptionToJava(Option<T> o) {
        if (o.isDefined()) {
            return Optional.of(o.get());
        } else {
            return Optional.empty();
        }
    }

    public static scala.concurrent.duration.FiniteDuration javaDurationToScala(Duration duration) {
        return scala.concurrent.duration.Duration.fromNanos(duration.toNanos());
    }

    public static Duration scalaDurationToJava(scala.concurrent.duration.Duration duration) {
        return Duration.of(duration.toNanos(), ChronoUnit.NANOS);
    }

    public static Option<scala.concurrent.duration.FiniteDuration> javaOptionalDurationToScala(Optional<Duration> duration) {
        return javaOptionalToScala(duration.map(d -> javaDurationToScala(d)));
    }

    public static Optional<Duration> scalaOptionDurationToJava(Option<scala.concurrent.duration.FiniteDuration> duration) {
        return scalaOptionToJava(duration).map(d -> scalaDurationToJava(d));
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

    public static IORuntime runtime = IORuntime$.MODULE$.global();

    public static ConcurrentEffect<IO> ioConcurrentEffect = (ConcurrentEffect<IO>) package$.MODULE$.ioConcurrentEffect();

    public static TemporalEffect<IO> ioTemporalEffect = (TemporalEffect<IO>) package$.MODULE$.ioTemporalEffect();

    public static AsyncEffect<IO> ioAsyncEffect = (AsyncEffect<IO>) package$.MODULE$.ioAsyncEffect();
}
