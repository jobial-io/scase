package io.jobial.scase.pulsar.javadsl;

import io.jobial.scase.marshalling.Marshalling;
import io.jobial.scase.pulsar.PulsarServiceConfiguration$;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static io.jobial.scase.core.javadsl.JavaUtils.*;
import static org.apache.pulsar.client.api.SubscriptionInitialPosition.Latest;


public class PulsarServiceConfiguration {

    public static <REQ, RESP> PulsarRequestResponseServiceConfiguration<REQ, RESP> requestResponse(
            String requestTopic,
            Optional<String> responseTopicOverride,
            Optional<Duration> batchingMaxPublishDelay,
            Optional<Duration> patternAutoDiscoveryPeriod,
            Optional<SubscriptionInitialPosition> subscriptionInitialPosition,
            Optional<Instant> subscriptionInitialPublishTime,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return new PulsarRequestResponseServiceConfiguration(PulsarServiceConfiguration$.MODULE$.requestResponse(
                requestTopic,
                javaOptionalToScala(responseTopicOverride),
                javaOptionalDurationToScala(batchingMaxPublishDelay),
                javaOptionalDurationToScala(patternAutoDiscoveryPeriod),
                javaOptionalToScala(subscriptionInitialPosition),
                javaOptionalToScala(subscriptionInitialPublishTime),
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
        return requestResponse(
                requestTopic,
                scalaOptionToJava(PulsarServiceConfiguration$.MODULE$.requestResponse$default$2()),
                scalaOptionDurationToJava(PulsarServiceConfiguration$.MODULE$.requestResponse$default$3()),
                scalaOptionDurationToJava(PulsarServiceConfiguration$.MODULE$.requestResponse$default$4()),
                scalaOptionToJava(PulsarServiceConfiguration$.MODULE$.requestResponse$default$5()),
                scalaOptionToJava(PulsarServiceConfiguration$.MODULE$.requestResponse$default$6()),
                requestMarshalling,
                responseMarshalling
        );
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Optional<Duration> batchingMaxPublishDelay,
            Optional<Duration> patternAutoDiscoveryPeriod,
            Optional<SubscriptionInitialPosition> subscriptionInitialPosition,
            Optional<Instant> subscriptionInitialPublishTime,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return new PulsarStreamServiceConfiguration(PulsarServiceConfiguration$.MODULE$.stream(
                requestTopic,
                responseTopic,
                javaOptionalDurationToScala(batchingMaxPublishDelay),
                javaOptionalDurationToScala(patternAutoDiscoveryPeriod),
                javaOptionalToScala(subscriptionInitialPosition),
                javaOptionalToScala(subscriptionInitialPublishTime),
                requestMarshalling.marshaller(),
                requestMarshalling.unmarshaller(),
                responseMarshalling.marshaller(),
                responseMarshalling.unmarshaller(),
                responseMarshalling.eitherMarshaller(),
                responseMarshalling.eitherUnmarshaller()
        ));
    }

    public static <REQ, RESP> PulsarStreamServiceConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return stream(
                requestTopic,
                responseTopic,
                Optional.of(Duration.ofMillis(1)),
                Optional.of(Duration.ofSeconds(1)),
                Optional.of(Latest),
                Optional.empty(),
                requestMarshalling,
                responseMarshalling
        );
    }

    public static <REQ, RESP> PulsarStreamServiceWithErrorTopicConfiguration<REQ, RESP> stream(
            String requestTopic,
            String responseTopic,
            String errorTopic,
            Optional<Duration> batchingMaxPublishDelay,
            Optional<Duration> patternAutoDiscoveryPeriod,
            Optional<SubscriptionInitialPosition> subscriptionInitialPosition,
            Optional<Instant> subscriptionInitialPublishTime,
            Marshalling<REQ> requestMarshalling,
            Marshalling<RESP> responseMarshalling
    ) {
        return new PulsarStreamServiceWithErrorTopicConfiguration(PulsarServiceConfiguration$.MODULE$.stream(
                requestTopic,
                responseTopic,
                errorTopic,
                javaOptionalDurationToScala(batchingMaxPublishDelay),
                javaOptionalDurationToScala(patternAutoDiscoveryPeriod),
                javaOptionalToScala(subscriptionInitialPosition),
                javaOptionalToScala(subscriptionInitialPublishTime),
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
                Optional.of(Duration.ofMillis(1)),
                Optional.of(Duration.ofSeconds(1)),
                Optional.of(Latest),
                Optional.empty(),
                requestMarshalling,
                responseMarshalling
        );
    }

    public static <M> PulsarMessageSourceServiceConfiguration<M> source(
            String topic,
            Optional<Duration> patternAutoDiscoveryPeriod,
            Optional<SubscriptionInitialPosition> subscriptionInitialPosition,
            Optional<Instant> subscriptionInitialPublishTime,
            Marshalling<M> marshalling
    ) {
        return new PulsarMessageSourceServiceConfiguration(PulsarServiceConfiguration$.MODULE$.source(
                topic,
                javaOptionalDurationToScala(patternAutoDiscoveryPeriod),
                javaOptionalToScala(subscriptionInitialPosition),
                javaOptionalToScala(subscriptionInitialPublishTime),
                marshalling.unmarshaller()
        ));
    }

    public static <M> PulsarMessageSourceServiceConfiguration<M> source(
            String topic,
            Marshalling<M> marshalling
    ) {
        return source(
                topic,
                scalaOptionDurationToJava(PulsarServiceConfiguration$.MODULE$.source$default$2()),
                scalaOptionToJava(PulsarServiceConfiguration$.MODULE$.source$default$3()),
                scalaOptionToJava(PulsarServiceConfiguration$.MODULE$.source$default$4()),
                marshalling
        );
    }

    public static <M> PulsarMessageHandlerServiceConfiguration<M> handler(
            String topic,
            Marshalling<M> marshalling
    ) {
        return handler(topic, Optional.empty(), Optional.empty(), Optional.empty(), PulsarServiceConfiguration$.MODULE$.handler$default$5(), marshalling);
    }

    public static <M> PulsarMessageHandlerServiceConfiguration<M> handler(
            String topic,
            Optional<Duration> patternAutoDiscoveryPeriod,
            Optional<SubscriptionInitialPosition> subscriptionInitialPosition,
            Optional<Instant> subscriptionInitialPublishTime,
            String subscriptionName,
            Marshalling<M> marshalling
    ) {
        return new PulsarMessageHandlerServiceConfiguration(PulsarServiceConfiguration$.MODULE$.handler(
                topic,
                javaOptionalToScala(patternAutoDiscoveryPeriod.map(t -> javaDurationToScala(t))),
                javaOptionalToScala(subscriptionInitialPosition),
                javaOptionalToScala(subscriptionInitialPublishTime),
                subscriptionName,
                marshalling.marshaller(),
                marshalling.unmarshaller()
        ));
    }

    public static <M> PulsarMessageDestinationServiceConfiguration<M> destination(
            String topic,
            Optional<Duration> batchingMaxPublishDelay,
            Marshalling<M> marshalling
    ) {
        return new PulsarMessageDestinationServiceConfiguration(PulsarServiceConfiguration$.MODULE$.destination(
                topic,
                javaOptionalDurationToScala(batchingMaxPublishDelay),
                marshalling.marshaller()
        ));
    }

    public static <M> PulsarMessageDestinationServiceConfiguration<M> destination(
            String topic,
            Marshalling<M> marshalling
    ) {
        return destination(
                topic,
                scalaOptionDurationToJava(PulsarServiceConfiguration$.MODULE$.destination$default$2()),
                marshalling
        );
    }
}