package com.sealmail.infra.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ComponentScan(basePackages = "com.sealmail")
@EnableTransactionManagement
public class SealMailApplication {

    public static void main(String[] args) {
        SpringApplication.run(SealMailApplication.class, args);
    }

}
