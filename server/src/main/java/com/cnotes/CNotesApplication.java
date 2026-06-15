package com.cnotes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.cnotes.**.mapper")
public class CNotesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CNotesApplication.class, args);
    }
}
