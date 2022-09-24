package io.jobial.scase.core.javadsl;

import scala.Some;

import java.time.Duration;
import java.util.Map;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.javaDurationToScala;
import static io.jobial.scase.core.impl.javadsl.JavaUtils.javaMapToScala;

public class SendRequestContext {

    private volatile io.jobial.scase.core.SendRequestContext context = package$.MODULE$.defaultSendRequestContext();

    public SendRequestContext() {
    }
    
    public SendRequestContext(Duration timeout) {
        setTimeout(timeout);
    }
    
    public void setTimeout(Duration timeout) {
        context = context.copy(Some.apply(javaDurationToScala(timeout)), context.attributes());
    }
    
    public void setAttributes(Map<String, String> attributes) {
        context = context.copy(context.requestTimeout(), javaMapToScala(attributes));
    }

    public io.jobial.scase.core.SendRequestContext getContext() {
        return context;
    }
}
