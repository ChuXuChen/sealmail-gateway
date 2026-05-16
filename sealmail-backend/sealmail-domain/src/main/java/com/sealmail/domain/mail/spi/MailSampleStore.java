package com.sealmail.domain.mail.spi;

public interface MailSampleStore {

    StoredMailSample store(byte[] mailContent, String filenameHint);

    record StoredMailSample(String location) {
    }
}
