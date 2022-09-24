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

import io.jobial.scase.core.TestRequest;
import io.jobial.scase.core.TestRequest1;
import io.jobial.scase.core.TestResponse;
import io.jobial.scase.core.TestResponse1;
import io.jobial.scase.core.impl.javadsl.FutureRequestHandler;
import io.jobial.scase.marshalling.serialization.javadsl.SerializationMarshalling;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.assertEquals;

public class PulsarServiceTest {

    FutureRequestHandler<TestRequest<? extends TestResponse>, TestResponse> requestHandler = request -> {
        if (request instanceof TestRequest1) {
            return completedFuture(new TestResponse1((TestRequest1) request, "hello " + ((TestRequest1) request).id()));
        }
        return null;
    };

    @Test
    public void testRequestResponseService() throws ExecutionException, InterruptedException {
        PulsarRequestResponseServiceConfiguration<TestRequest<? extends TestResponse>, TestResponse> serviceConfig =
                PulsarServiceConfiguration.requestResponse("hello-test-${uuid(6)}", new SerializationMarshalling());

        var service = serviceConfig.service(requestHandler);
        service.start();

        var client = serviceConfig.client();
        var request = new TestRequest1("world");
        var response = client.sendRequest(request)
                .whenComplete((r, error) -> System.out.println(r))
                .get();

        assertEquals(response, new TestResponse1(request, "hello world"));
        Thread.sleep(1000);

//            for {
//                service < -serviceConfig.service(requestHandler)
//                client < -serviceConfig.client[IO]
//                r < -testSuccessfulReply(service, client)
//            } yield r
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
