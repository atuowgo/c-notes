package com.cnotes;

import com.cnotes.chat.embedding.ArkEmbeddingProperties;
import com.cnotes.plaza.PlazaScoreProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ArkEmbeddingProperties.class, PlazaScoreProperties.class})
@MapperScan("com.cnotes.**.mapper")
public class CNotesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CNotesApplication.class, args);
    }
}
