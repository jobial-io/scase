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
import io.jobial.scase.core.javadsl.RequestHandler;
import io.jobial.scase.marshalling.tibrv.raw.javadsl.TibrvMsgRawMarshalling;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.javadsl.JavaUtils.uuid;
import static io.jobial.scase.pulsar.javadsl.PulsarServiceConfiguration.requestResponse;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.Assert.assertEquals;


public class PulsarWithTibrvMsgTest {

    RequestHandler<TibrvMsg, TibrvMsg> requestHandler = (request, context) -> supplyAsync(() -> {
        try {
            var response = new TibrvMsg();
            response.add("greeting", "hello " + request.get("name"));
            return response;
        } catch (TibrvException e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void testRequestResponseService() throws ExecutionException, InterruptedException, RequestTimeout, TibrvException {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new TibrvMsgRawMarshalling(), new TibrvMsgRawMarshalling());

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

}
