package io.jobial.scase.core.javadsl;

import cats.effect.IO;

public class MessageContext {

    private volatile io.jobial.scase.core.MessageContext<IO> context;

    public MessageContext(io.jobial.scase.core.MessageContext<IO> context) {
        this.context = context;
    }
    
    public io.jobial.scase.core.MessageContext<IO> getContext() {
        return context;
    }
}
