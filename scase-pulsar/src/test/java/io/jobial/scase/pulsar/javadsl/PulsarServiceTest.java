/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.core.RequestTimeout;
import io.jobial.scase.core.javadsl.MessageHandler;
import io.jobial.scase.core.javadsl.RequestHandler;
import io.jobial.scase.core.javadsl.SendRequestContext;
import io.jobial.scase.core.test.*;
import io.jobial.scase.marshalling.serialization.SerializationMarshalling;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.uuid;
import static io.jobial.scase.pulsar.javadsl.PulsarServiceConfiguration.*;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.Assert.assertEquals;

class TestException1 extends IllegalStateException {
}

class TestException2 extends IllegalStateException {
}

public class PulsarServiceTest {

    RequestHandler<TestRequest, TestResponse> requestHandler = (request, context) -> {
        if (request instanceof TestRequest1) {
            return completedFuture(new TestResponse1((TestRequest1) request, "hello " + ((TestRequest1) request).id()));
        } else if (request instanceof TestRequest2) {
            return completedFuture(new TestResponse2((TestRequest2) request, "hi " + ((TestRequest2) request).id()));
        }
        return null;
    };

    RequestHandler<TestRequest, TestResponse> requestHandlerWithError = (request, context) -> {
        if (request instanceof TestRequest1) {
            throw new TestException1();
        } else if (request instanceof TestRequest2) {
            return failedFuture(new TestException2());
        }
        return null;
    };

    MessageHandler<TestRequest> messageHandler(CompletableFuture<TestRequest> received) {
        return (message, context) -> {
            received.complete(message);
        };
    }

    @Test
    public void testRequestResponseService() throws ExecutionException, InterruptedException, RequestTimeout {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandler).get();
        var state = service.start().get();

        var client = serviceConfig.client().get();
        var request = new TestRequest1("world");
        var response = client.sendRequest(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        assertEquals(response, new TestResponse1(request, "hello world"));
        state.stop().whenComplete((r, error) -> System.out.println("stopped service"));
    }

    @Test(expected = RequestTimeout.class)
    public void testRequestTimeoutIfServiceIsNotStarted() throws Throwable {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandler);

        var client = serviceConfig.client().get();
        var request = new TestRequest1("world");
        try {
            client.sendRequest(request, new SendRequestContext(ofSeconds(1))).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = TestException1.class)
    public void testServiceDirectException() throws Throwable {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandlerWithError).get();
        var state = service.start().get();

        var client = serviceConfig.client().get();
        var request = new TestRequest1("world");
        try {
            client.sendRequest(request).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        } finally {
            state.stop();
        }
    }

    @Test(expected = TestException2.class)
    public void testServiceFutureWithException() throws Throwable {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandlerWithError).get();
        var state = service.start().get();

        var client = serviceConfig.client().get();
        var request = new TestRequest2("world");
        try {
            client.sendRequest(request).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        } finally {
            state.stop();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testServiceReturnsNull() throws Throwable {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandlerWithError).get();
        var state = service.start().get();

        var client = serviceConfig.client().get();
        var request = new TestRequest3("world");
        try {
            client.sendRequest(request).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        } finally {
            state.stop();
        }
    }

    @Test
    public void testStreamService() throws ExecutionException, InterruptedException, RequestTimeout {
        var serviceConfig =
                stream("hello-test-" + uuid(6), "hello-test-response-" + uuid(6), new SerializationMarshalling<TestRequest>(), new SerializationMarshalling<TestResponse>());

        var service = serviceConfig.service(requestHandler).get();
        var state = service.start().get();

        var senderClient = serviceConfig.senderClient().get();
        var receiverClient = serviceConfig.receiverClient().get();
        var request = new TestRequest1("world");
        senderClient.send(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        var response = receiverClient.receive().get();
        assertEquals(response.right().get(), new TestResponse1(request, "hello world"));
        state.stop().whenComplete((r, error) -> System.out.println("stopped service"));
    }

    @Test
    public void testMessageHandlerService() throws ExecutionException, InterruptedException, RequestTimeout {
        var serviceConfig = handler("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest>());

        var received = new CompletableFuture<TestRequest>();
        var service = serviceConfig.service(messageHandler(received)).get();
        var state = service.start().get();

        var client = serviceConfig.client().get();
        var request = new TestRequest1("world");
        client.send(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        assertEquals(request, received.get());
        state.stop().whenComplete((r, error) -> System.out.println("stopped service"));
    }
}
