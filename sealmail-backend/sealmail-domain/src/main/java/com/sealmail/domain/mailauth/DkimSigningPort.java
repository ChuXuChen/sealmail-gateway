package com.sealmail.domain.mailauth;

public interface DkimSigningPort {

    SigningResult sign(byte[] rawContent, DomainMailAuthPolicy policy);
}
