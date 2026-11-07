package com.example.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.example.interview.mapper")
@ConfigurationPropertiesScan
public class InterviewSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewSystemApplication.class, args);
    }
}
