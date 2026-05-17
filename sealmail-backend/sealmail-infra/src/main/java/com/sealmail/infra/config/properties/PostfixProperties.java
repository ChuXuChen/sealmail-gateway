package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.postfix")
public class PostfixProperties {

    private boolean enabled = true;
    private String host = "127.0.0.1";
    private int afterFilterPort = 10026;
    private int outboundPort = 10027;
    private int timeout = 10000;
    private String envelopeFrom;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getAfterFilterPort() {
        return afterFilterPort;
    }

    public void setAfterFilterPort(int afterFilterPort) {
        this.afterFilterPort = afterFilterPort;
    }

    public int getOutboundPort() {
        return outboundPort;
    }

    public void setOutboundPort(int outboundPort) {
        this.outboundPort = outboundPort;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getEnvelopeFrom() {
        return envelopeFrom;
    }

    public void setEnvelopeFrom(String envelopeFrom) {
        this.envelopeFrom = envelopeFrom;
    }
}
