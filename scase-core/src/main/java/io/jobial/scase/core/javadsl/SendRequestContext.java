package io.jobial.scase.core.javadsl;

import scala.Some;

import java.time.Duration;
import java.util.Map;

import static io.jobial.scase.core.impl.javadsl.JavaUtils.javaDurationToScala;
import static io.jobial.scase.core.impl.javadsl.JavaUtils.javaMapToScala;
import static io.jobial.scase.core.javadsl.Defaults.defaultSendRequestContext;

public class SendRequestContext {

    private volatile io.jobial.scase.core.SendRequestContext sendRequestContext = defaultSendRequestContext;

    public SendRequestContext() {
    }
    
    public SendRequestContext(Duration timeout) {
        setTimeout(timeout);
    }
    
    public void setTimeout(Duration timeout) {
        sendRequestContext = sendRequestContext.copy(Some.apply(javaDurationToScala(timeout)), sendRequestContext.attributes());
    }
    
    public void setAttributes(Map<String, String> attributes) {
        sendRequestContext = sendRequestContext.copy(sendRequestContext.requestTimeout(), javaMapToScala(attributes));
    }

    public io.jobial.scase.core.SendRequestContext getSendRequestContext() {
        return sendRequestContext;
    }
}
