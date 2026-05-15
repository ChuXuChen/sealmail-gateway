package com.sealmail.app.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.sealmail.app.usecase")
public class UseCaseConfig {
    // Use cases will be picked up via component scanning
}
