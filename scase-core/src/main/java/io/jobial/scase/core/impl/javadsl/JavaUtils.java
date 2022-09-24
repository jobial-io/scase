package io.jobial.scase.core.impl.javadsl;

import cats.effect.*;
import io.jobial.scase.util.Hash$;
import scala.Function0;
import scala.Function1;
import scala.concurrent.*;
import scala.util.Success$;
import scala.util.Try;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class JavaUtils {

    public static <T> CompletableFuture<T> scalaFutureToCompletableFuture(Future<T> f) {
        return package$.MODULE$.scalaFutureToCompletableFuture(f, executionContext);
    }

    public static <T> CompletableFuture<T> ioToCompletableFuture(IO<T> io) {
        return scalaFutureToCompletableFuture(io.unsafeToFuture());
    }

    public static <T> IO<T> completableFutureToIO(final CompletableFuture<T> f) {
        return package$.MODULE$.completableFutureToIO(f, contextShift);
    }

    private static <T> Future<T> completableFutureToScalaFuture(CompletableFuture<T> f) {
        return package$.MODULE$.completableFutureToScalaFuture(f);
    }
    
    public static scala.concurrent.duration.Duration javaDurationToScala(Duration duration) {
        return scala.concurrent.duration.Duration.fromNanos(duration.toNanos());
    }

    public static <A, B> scala.collection.immutable.Map<A, B> javaMapToScala(Map<A, B> map) {
        return package$.MODULE$.javaMapToScala(map);
    }

    public static <A, B> Function1<A, B> javaFunctionToScala(Function<A, B> f) {
        return package$.MODULE$.javaFunctionToScala(f);
    }
    
    public static String uuid(int length) {
        return Hash$.MODULE$.uuid(length, 36);
    }

    public static ExecutionContext executionContext = ExecutionContext$.MODULE$.global();

    public static ContextShift<IO> contextShift = IO.contextShift(executionContext);

    public static Concurrent<IO> concurrent = IO$.MODULE$.ioConcurrentEffect(contextShift);

    public static Timer<IO> timer = IO$.MODULE$.timer(executionContext);

}
