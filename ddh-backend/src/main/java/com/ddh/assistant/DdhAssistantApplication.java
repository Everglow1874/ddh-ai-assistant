package com.ddh.assistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ddh.assistant.mapper")
public class DdhAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdhAssistantApplication.class, args);
    }
}
