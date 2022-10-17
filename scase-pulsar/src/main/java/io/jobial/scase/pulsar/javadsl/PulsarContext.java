package io.jobial.scase.pulsar.javadsl;

public class PulsarContext {

    private volatile io.jobial.scase.pulsar.PulsarContext context = io.jobial.scase.pulsar.javadsl.package$.MODULE$.defaultPulsarContext();

    public PulsarContext() {
    }

    public PulsarContext(io.jobial.scase.pulsar.PulsarContext context) {
        this.context = context;
    }

    public io.jobial.scase.pulsar.PulsarContext getContext() {
        return context;
    }
    
    public PulsarContext withHost(String host) {
        return new PulsarContext(context.copy(host, context.port(), context.tenant(), context.namespace(), context.useDaemonThreadsInClient()));
    }

    public PulsarContext withPort(int port) {
        return new PulsarContext(context.copy(context.host(), port, context.tenant(), context.namespace(), context.useDaemonThreadsInClient()));
    }

    public PulsarContext withTenant(String tenant) {
        return new PulsarContext(context.copy(context.host(), context.port(), tenant, context.namespace(), context.useDaemonThreadsInClient()));
    }

    public PulsarContext withNamespace(String namespace) {
        return new PulsarContext(context.copy(context.host(), context.port(), context.tenant(), namespace, context.useDaemonThreadsInClient()));
    }

    public PulsarContext withUseDaemonThreadsInClient(Boolean useDaemonThreadsInClient) {
        return new PulsarContext(context.copy(context.host(), context.port(), context.tenant(), context.namespace(), useDaemonThreadsInClient));
    }
}
