package com.sealmail.domain.mailauth;

public interface TrustedMailSourcePort {

    MailSourceIdentity resolve(byte[] rawContent, MailSourceIdentity candidate, MailAuthPolicy policy);
}
