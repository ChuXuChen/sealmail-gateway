package com.sealmail.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
        "com.sealmail.boot",
        "com.sealmail.web",
        "com.sealmail.app",
        "com.sealmail.infra"
})
@ConfigurationPropertiesScan(basePackages = "com.sealmail.infra.config.properties")
@EntityScan(basePackages = "com.sealmail.infra.persistence.entity")
public class SealMailGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SealMailGatewayApplication.class, args);
    }
}
