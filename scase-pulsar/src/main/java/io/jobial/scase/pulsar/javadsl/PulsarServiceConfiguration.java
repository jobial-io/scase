package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.marshalling.javadsl.Marshalling;
import io.jobial.scase.pulsar.PulsarServiceConfiguration$;
import scala.None$;
import scala.Option;
import scala.Option$;

import java.time.Duration;


public class PulsarServiceConfiguration {

    public static <REQ, RESP> PulsarRequestResponseServiceConfiguration<REQ, RESP> requestResponse(
            String requestTopic,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling,
            Duration batchingMaxPublishDelay
    ) {
        return new PulsarRequestResponseServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<REQ, RESP>requestResponse(
                requestTopic,
                Option$.MODULE$.empty(),
                scala.concurrent.duration.Duration.fromNanos(batchingMaxPublishDelay.toNanos()),
                requestMarshalling.marshaller(),
                requestMarshalling.unmarshaller(),
                responseMarshalling.marshaller(),
                responseMarshalling.unmarshaller(),
                responseMarshalling.eitherMarshaller(),
                responseMarshalling.eitherUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarRequestResponseServiceConfiguration<REQ, RESP> requestResponse(
            String requestTopic,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return requestResponse(requestTopic, requestMarshalling, responseMarshalling, Duration.ofMillis(1));
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Duration batchingMaxPublishDelay,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return new PulsarStreamServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<REQ, RESP>stream(
                requestTopic,
                responseTopic,
                scala.concurrent.duration.Duration.fromNanos(batchingMaxPublishDelay.toNanos()),
                requestMarshalling.marshaller(),
                requestMarshalling.unmarshaller(),
                responseMarshalling.marshaller(),
                responseMarshalling.unmarshaller(),
                responseMarshalling.throwableMarshaller(),
                responseMarshalling.throwableUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return stream(requestTopic, responseTopic, Duration.ofMillis(1), requestMarshalling, responseMarshalling);
    }

    public static <REQ, RESP> PulsarStreamServiceWithErrorTopicConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            String errorTopic,
            Duration batchingMaxPublishDelay,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return new PulsarStreamServiceWithErrorTopicConfiguration(PulsarServiceConfiguration$.MODULE$.<REQ, RESP>stream(
                requestTopic,
                responseTopic,
                errorTopic,
                scala.concurrent.duration.Duration.fromNanos(batchingMaxPublishDelay.toNanos()),
                requestMarshalling.marshaller(),
                requestMarshalling.unmarshaller(),
                responseMarshalling.marshaller(),
                responseMarshalling.unmarshaller(),
                responseMarshalling.throwableMarshaller(),
                responseMarshalling.throwableUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarStreamServiceWithErrorTopicConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            String errorTopic,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return stream(
                requestTopic,
                responseTopic,
                errorTopic,
                Duration.ofMillis(1),
                requestMarshalling,
                responseMarshalling
        );
    }

    public static <M> PulsarMessageSourceServiceConfiguration<M> source(
            String topic,
            Marshalling<M> marshalling
    ) {
        return new PulsarMessageSourceServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<M>source(
                topic,
                marshalling.unmarshaller()
        ));
    }

    public static <M> PulsarMessageHandlerServiceConfiguration<M> handler(
            String topic,
            Marshalling<M> marshalling
    ) {
        return new PulsarMessageHandlerServiceConfiguration(PulsarServiceConfiguration$.MODULE$.<M>handler(
                topic,
                marshalling.marshaller(),
                marshalling.unmarshaller()
        ));
    }
}