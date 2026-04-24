package com.sealmail.sealmailcore;

import org.springframework.stereotype.Service;

@Service
public class MailService {

    public String getStatus() {
        return "Mail system ready.";
    }
}