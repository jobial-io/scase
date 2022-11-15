package io.jobial.scase.core.javadsl;

import java.util.Map;

import static io.jobial.scase.core.javadsl.JavaUtils.javaMapToScala;

public class SendMessageContext {

    private volatile io.jobial.scase.core.SendMessageContext context = package$.MODULE$.defaultSendMessageContext();

    public SendMessageContext() {
    }

    public void setAttributes(Map<String, String> attributes) {
        context = context.copy(javaMapToScala(attributes));
    }

    public io.jobial.scase.core.SendMessageContext getContext() {
        return context;
    }
}
