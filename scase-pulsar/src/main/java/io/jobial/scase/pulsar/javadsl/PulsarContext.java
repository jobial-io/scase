package io.jobial.scase.pulsar.javadsl;

public class PulsarContext {

    private volatile io.jobial.scase.pulsar.PulsarContext context = io.jobial.scase.pulsar.javadsl.package$.MODULE$.defaultPulsarContext();

    public PulsarContext() {
    }

    public io.jobial.scase.pulsar.PulsarContext getContext() {
        return context;
    }
}
