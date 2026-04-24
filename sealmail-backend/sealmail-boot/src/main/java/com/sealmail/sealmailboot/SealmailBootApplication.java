package com.sealmail.sealmailboot;

import com.sealmail.sealmailcore.MailService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@ComponentScan("com.sealmail")
@RestController
public class SealmailBootApplication {

    private final MailService mailService;

    // 构造函数注入 MailService（Spring 会自动装配）
    public SealmailBootApplication(MailService mailService) {
        this.mailService = mailService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SealmailBootApplication.class, args);
    }

    @GetMapping("/")
    public String health() {
        return "Sealmail Gateway is running! " + mailService.getStatus();
    }
}