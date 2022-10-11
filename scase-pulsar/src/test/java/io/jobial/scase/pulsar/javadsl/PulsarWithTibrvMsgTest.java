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

import com.tibco.tibrv.TibrvException;
import com.tibco.tibrv.TibrvMsg;
import io.jobial.scase.core.RequestTimeout;
import io.jobial.scase.core.javadsl.MessageHandler;
import io.jobial.scase.core.javadsl.RequestHandler;
import io.jobial.scase.marshalling.tibrv.raw.javadsl.TibrvMsgRawMarshalling;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.uuid;
import static io.jobial.scase.pulsar.javadsl.PulsarServiceConfiguration.*;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.Assert.assertEquals;


public class PulsarWithTibrvMsgTest {

    TibrvMsgRawMarshalling tibrvMarshalling = new TibrvMsgRawMarshalling();

    RequestHandler<TibrvMsg, TibrvMsg> requestHandler = (request, context) -> supplyAsync(() -> {
        try {
            var response = new TibrvMsg();
            response.add("greeting", "hello " + request.get("name"));
            return response;
        } catch (TibrvException e) {
            throw new RuntimeException(e);
        }
    });

    MessageHandler<TibrvMsg> messageHandler = (request, context) -> runAsync(() -> {
        try {
            var targetTopic = request.get("target_topic").toString();
            var response = new TibrvMsg();
            response.add("greeting", "hello on " + targetTopic);

            // Creating client for response, could be cached...
            var client = destination(targetTopic, tibrvMarshalling).client();
            System.out.println("sending to " + targetTopic);
            client.send(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void testRequestResponseService() throws ExecutionException, InterruptedException, RequestTimeout, TibrvException {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new TibrvMsgRawMarshalling(), tibrvMarshalling);

        var service = serviceConfig.service(requestHandler);
        var state = service.start().get();

        var client = serviceConfig.client();
        var request = new TibrvMsg();
        request.add("name", "world");

        var response = client.sendRequest(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        assertEquals(response.get("greeting"), "hello world");
        state.stop().whenComplete((r, error) -> System.out.println("stopped service"));
    }

    @Test
    public void testMessageHandlerService() throws ExecutionException, InterruptedException, RequestTimeout, TibrvException {
        PulsarMessageHandlerServiceConfiguration<TibrvMsg> serviceConfig =
                handler("test-topic-" + uuid(6), tibrvMarshalling);
        var service = serviceConfig.service(messageHandler);
        var state = service.start().get();

        var testTopicPrefix = "test-topic-response-" + uuid(6) + "-";
        for (int i = 0; i < 10; i++) {
            var topic = testTopicPrefix + i;
            var client = serviceConfig.client();
            var request = new TibrvMsg();
            request.add("target_topic", topic);
            client.send(request).get();

            // Receiving the response sent out by the server:
            source(topic, tibrvMarshalling).client().receive().whenComplete((response, error) ->
                    System.out.println(response)
            ).get();
        }


    }

}
