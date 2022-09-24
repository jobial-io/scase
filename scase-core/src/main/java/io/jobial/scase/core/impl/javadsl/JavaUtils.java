package io.jobial.scase.core.impl.javadsl;

import cats.effect.*;
import scala.Function0;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.Promise$;
import scala.util.Success$;
import scala.util.Try;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class JavaUtils {
    
    public static <T> CompletableFuture<T> scalaFutureToCompletableFuture(Future<T> f) {
        var r = new CompletableFuture<T>();

        f.onComplete(new Function1<Try<T>, Void>() {
            @Override
            public Void apply(Try<T> v1) {
                if (v1.isSuccess()) r.complete(v1.get());
                else r.completeExceptionally(v1.failed().get());

                return null;
            }
        }, executionContext);

        return r;
    }

    public static <T> CompletableFuture<T> ioToCompletableFuture(IO<T> io) {
        return scalaFutureToCompletableFuture(io.unsafeToFuture());
    }

    public static <T> IO<T> completableFutureToIO(final CompletableFuture<T> f) {
        return IO$.MODULE$.fromFuture(IO$.MODULE$.apply(new Function0<Future<T>>() {
            @Override
            public Future<T> apply() {
                return completableFutureToScalaFuture(f);
            }
        }), contextShift);
    }

    private static <T> Future<T> completableFutureToScalaFuture(CompletableFuture<T> f) {
        Promise<T> p = Promise$.MODULE$.apply();
        f.whenComplete((r, e) -> {
            if (e != null)
                p.failure(e);
            else
                p.complete(Success$.MODULE$.apply(r));
        });
        return p.future();
    }

    public static <T, R> Function1<T, R> javaFunctionToScala(Function<T, R> f) {
        return new Function1<T, R>() {
            @Override
            public R apply(T v1) {
                return f.apply(v1);
            }
        };
    }

    public static ExecutionContext executionContext = ExecutionContext.global();

    public static ContextShift<IO> contextShift = IO.contextShift(executionContext);

    public static Concurrent<IO> concurrent = IO$.MODULE$.ioConcurrentEffect(contextShift);

    public static Timer<IO> timer = IO$.MODULE$.timer(executionContext);

}
