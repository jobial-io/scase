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

import io.jobial.scase.core.*;
import io.jobial.scase.core.impl.javadsl.FutureRequestHandler;
import io.jobial.scase.core.javadsl.SendRequestContext;
import io.jobial.scase.marshalling.serialization.javadsl.SerializationMarshalling;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.uuid;
import static io.jobial.scase.pulsar.javadsl.PulsarServiceConfiguration.requestResponse;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.assertEquals;

public class PulsarServiceTest {

    FutureRequestHandler<TestRequest, TestResponse> requestHandler = request -> {
        if (request instanceof TestRequest1) {
            return completedFuture(new TestResponse1((TestRequest1) request, "hello " + ((TestRequest1) request).id()));
        } else if (request instanceof TestRequest2) {
            return completedFuture(new TestResponse2((TestRequest2) request, "hi " + ((TestRequest2) request).id()));
        }
        return null;
    };

    @Test
    public void testRequestResponseService() throws ExecutionException, InterruptedException {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest, TestResponse>());

        var service = serviceConfig.service(requestHandler);
        var state = service.start();

        var client = serviceConfig.client();
        var request = new TestRequest1("world");
        var response = client.sendRequest(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        assertEquals(response, new TestResponse1(request, "hello world"));
        Thread.sleep(1000);
        state.get().stop().whenComplete((r, error) -> System.out.println("stopped service"));
        Thread.sleep(1000);
    }

    @Test(expected = RequestTimeout.class)
    public void testRequestTimeoutIfServiceIsNotStarted() throws Throwable {
        var serviceConfig =
                requestResponse("hello-test-" + uuid(6), new SerializationMarshalling<TestRequest, TestResponse>());

        var service = serviceConfig.service(requestHandler);

        var client = serviceConfig.client();
        var request = new TestRequest1("world");
        try {
            client.sendRequest(request, new SendRequestContext(ofSeconds(1))).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    //    <REQ, RESP> void testRequestResponse(FutureRequestHandler<REQ, RESP> testRequestProcessor, REQ request, RESP response) {
//        PulsarServiceConfiguration serviceConfig = PulsarServiceConfiguration$.MODULE$.<TestRequest<? extends TestResponse>, TestResponse>requestResponse("hello-test-${uuid(6)}");
//
//    }

//    for {
//      testMessageConsumer <- InMemoryConsumerProducer[IO, REQ]
//      testMessageProducer <- InMemoryConsumerProducer[IO, Either[Throwable, RESP]]
//      service <- ConsumerProducerRequestResponseService[IO, REQ, RESP](
//        testMessageConsumer,
//        { _: Option[String] => IO(testMessageProducer) },
//        testRequestProcessor
//      )
//      s <- service.start
//      d <- Deferred[IO, Either[Throwable, RESP]]
//      _ <- testMessageProducer.subscribe({ m =>
//        println("complete")
//        for {
//          message <- m.message
//          r <- d.complete(message)
//        } yield r
//      })
//      _ <- testMessageConsumer.send(request, Map(ResponseProducerIdKey -> ""))
//      r <- d.get
//    } yield assert(r == response)
//
//  def testRequestResponseClient[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Eq](testRequestProcessor: RequestHandler[IO, REQ, RESP], request: REQUEST, response: Either[Throwable, RESPONSE])(
//    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
//  ) =
//    for {
//      testMessageConsumer <- InMemoryConsumerProducer[IO, REQ]
//      testMessageProducer <- InMemoryConsumerProducer[IO, Either[Throwable, RESP]]
//      service <- ConsumerProducerRequestResponseService[IO, REQ, RESP](
//        testMessageConsumer,
//        { _: Option[String] => IO(testMessageProducer) },
//        testRequestProcessor
//      )
//      s <- service.start
//      client <- ConsumerProducerRequestResponseClient[IO, REQ, RESP](
//        testMessageProducer,
//        () => testMessageConsumer,
//        None
//      )
//      r1 <- client ? request
//      r <- {
//        implicit val c = client
//        // We do it this way to test if the implicit request sending is working, obviously we could also use client.sendRequest here 
//        for {
//          r <- request
//        } yield r
//      }
//    } yield assert(Right(r) === response)
//
//  "request-response service" should "reply successfully" in {
//    testRequestResponse(
//      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
//        override def handleRequest(implicit context: RequestContext[IO]): Handler = {
//          case r: TestRequest1 =>
//            println("replying...")
//            r.reply(response1)
//        }
//      },
//      request1,
//      Right(response1)
//    )
//  }
//
//  "request-response service" should "reply with error" in {
//    testRequestResponse(
//      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
//        override def handleRequest(implicit context: RequestContext[IO]): Handler = {
//          case r: TestRequest1 =>
//            println("replying...")
//            r.reply(response1)
//          case r: TestRequest2 =>
//            IO.raiseError(TestException("exception!!!"))
//        }
//      },
//      request2,
//      Left(TestException("exception!!!"))
//    )
//  }
//
//  "request-response client" should "get reply successfully" in {
//    testRequestResponseClient(
//      new RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] {
//        override def handleRequest(implicit context: RequestContext[IO]) = {
//          case r: TestRequest1 =>
//            println("replying...")
//            r.reply(response1)
//        }
//      },
//      request1,
//      Right(response1)
//    )
//  }

}
