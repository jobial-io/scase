package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.marshalling.javadsl.Marshalling;
import io.jobial.scase.pulsar.PulsarServiceConfiguration$;

import java.time.Duration;


public class PulsarServiceConfiguration {

    public static <REQ, RESP> PulsarRequestResponseServiceConfiguration<REQ, RESP> requestResponse(
            String requestTopic,
            Marshalling<REQ, RESP> marshalling,
            Duration batchingMaxPublishDelay
    ) {
        return new PulsarRequestResponseServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<REQ, RESP>requestResponse(
                requestTopic,
                scala.concurrent.duration.Duration.fromNanos(batchingMaxPublishDelay.toNanos()),
                marshalling.requestMarshaller(),
                marshalling.requestUnmarshaller(),
                marshalling.responseMarshaller(),
                marshalling.responseUnmarshaller(),
                marshalling.responseOrThrowableMarshaller(),
                marshalling.responseOrThrowableUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarRequestResponseServiceConfiguration<REQ, RESP> requestResponse(
            String requestTopic,
            Marshalling<REQ, RESP> marshalling
    ) {
        return requestResponse(requestTopic, marshalling, Duration.ofMillis(1));
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Duration batchingMaxPublishDelay,
            Marshalling<REQ, RESP> marshalling
    ) {
        return new PulsarStreamServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<REQ, RESP>stream(
                requestTopic,
                responseTopic,
                scala.concurrent.duration.Duration.fromNanos(batchingMaxPublishDelay.toNanos()),
                marshalling.requestMarshaller(),
                marshalling.requestUnmarshaller(),
                marshalling.responseMarshaller(),
                marshalling.responseUnmarshaller(),
                marshalling.responseOrThrowableMarshaller(),
                marshalling.responseOrThrowableUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Marshalling<REQ, RESP> marshalling
    ) {
        return stream(requestTopic, responseTopic, Duration.ofMillis(1), marshalling);
    }

    public static <M> PulsarMessageSourceServiceConfiguration<M> source(
            String topic,
            Marshalling<M, M> marshalling
    ) {
        return new PulsarMessageSourceServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<M>source(
                topic,
                marshalling.responseUnmarshaller()
        ));
    }
}