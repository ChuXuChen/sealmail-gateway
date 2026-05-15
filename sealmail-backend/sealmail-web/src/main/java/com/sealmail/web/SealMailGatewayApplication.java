package com.sealmail.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = "com.sealmail")
@ConfigurationPropertiesScan(basePackages = "com.sealmail")
@EntityScan(basePackages = "com.sealmail.infra.persistence.entity")
public class SealMailGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SealMailGatewayApplication.class, args);
    }

}
