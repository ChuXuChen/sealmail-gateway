package com.sealmail.domain.policy;

public enum DeliveryTransportProfile {
    SMTP_CLEAR(25, SmtpChannelMode.CLEAR, TlsStack.NONE),
    SMTP_STARTTLS_STANDARD(587, SmtpChannelMode.STARTTLS, TlsStack.STANDARD),
    SMTP_IMPLICIT_TLS_STANDARD(465, SmtpChannelMode.IMPLICIT_TLS, TlsStack.STANDARD),
    SMTP_STARTTLS_GM(2525, SmtpChannelMode.STARTTLS, TlsStack.GM),
    SMTP_IMPLICIT_TLS_GM(2465, SmtpChannelMode.IMPLICIT_TLS, TlsStack.GM);

    private final int defaultPort;
    private final SmtpChannelMode channelMode;
    private final TlsStack tlsStack;

    DeliveryTransportProfile(int defaultPort, SmtpChannelMode channelMode, TlsStack tlsStack) {
        this.defaultPort = defaultPort;
        this.channelMode = channelMode;
        this.tlsStack = tlsStack;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public SmtpChannelMode channelMode() {
        return channelMode;
    }

    public TlsStack tlsStack() {
        return tlsStack;
    }

    public boolean requiresTls() {
        return channelMode != SmtpChannelMode.CLEAR;
    }

    public boolean usesStartTls() {
        return channelMode == SmtpChannelMode.STARTTLS;
    }

    public boolean usesImplicitTls() {
        return channelMode == SmtpChannelMode.IMPLICIT_TLS;
    }

    public boolean usesGmTls() {
        return tlsStack == TlsStack.GM;
    }

    public static DeliveryTransportProfile fromLegacyPort(Integer port) {
        if (port == null) {
            return SMTP_CLEAR;
        }
        return switch (port) {
            case 25 -> SMTP_CLEAR;
            case 587 -> SMTP_STARTTLS_STANDARD;
            case 465 -> SMTP_IMPLICIT_TLS_STANDARD;
            case 2525 -> SMTP_STARTTLS_GM;
            case 2465 -> SMTP_IMPLICIT_TLS_GM;
            default -> throw new IllegalArgumentException(
                    "投递端口由传输配置决定，仅支持 25/587/465/2525/2465");
        };
    }

    public enum SmtpChannelMode {
        CLEAR,
        STARTTLS,
        IMPLICIT_TLS
    }

    public enum TlsStack {
        NONE,
        STANDARD,
        GM
    }
}
