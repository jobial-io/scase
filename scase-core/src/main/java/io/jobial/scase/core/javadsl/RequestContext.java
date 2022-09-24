package io.jobial.scase.core.javadsl;

public class RequestContext {

    private volatile io.jobial.scase.core.RequestContext context;

    public RequestContext(io.jobial.scase.core.RequestContext context) {
        this.context = context;
    }
}
