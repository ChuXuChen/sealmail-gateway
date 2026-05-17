package com.sealmail.domain.mailauth;

public interface MailAuthVerifierPort {

    AuthenticationResultSet verify(byte[] rawContent, MailSourceIdentity sourceIdentity, MailAuthPolicy policy);
}
