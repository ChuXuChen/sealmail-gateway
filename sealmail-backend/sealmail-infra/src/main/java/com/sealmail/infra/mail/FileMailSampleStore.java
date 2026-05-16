package com.sealmail.infra.mail;

import com.sealmail.domain.mail.spi.MailSampleStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class FileMailSampleStore implements MailSampleStore {

    private final Path outputDirectory;

    public FileMailSampleStore(@Value("${sealmail.mail-sample-store.directory:/tmp}") String outputDirectory) {
        this.outputDirectory = Path.of(outputDirectory);
    }

    @Override
    public StoredMailSample store(byte[] mailContent, String filenameHint) {
        try {
            Files.createDirectories(outputDirectory);
            String safeHint = filenameHint == null || filenameHint.isBlank()
                    ? UUID.randomUUID().toString()
                    : filenameHint.replaceAll("[^A-Za-z0-9._-]", "_");
            Path target = outputDirectory.resolve(safeHint + ".eml");
            Files.write(target, mailContent);
            return new StoredMailSample(target.toString());
        } catch (Exception e) {
            throw new MailSampleStoreException("Failed to store mail sample: " + e.getMessage(), e);
        }
    }

    public static class MailSampleStoreException extends RuntimeException {
        public MailSampleStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
