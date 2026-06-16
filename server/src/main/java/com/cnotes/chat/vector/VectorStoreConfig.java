package com.cnotes.chat.vector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 本地文件型向量库装配:用 {@link SimpleVectorStore}(JSON 落盘)承载知识网的簇综述向量。
 * 注入的 EmbeddingModel 为 {@code @Primary} 的 ArkEmbeddingModel(火山引擎多模态),
 * Bean 构造期不触网;若磁盘已有快照则启动时加载,实现重启续用。
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    SimpleVectorStore simpleVectorStore(
            EmbeddingModel embeddingModel,
            @Value("${cnotes.vectorstore.path:./.data/vectorstore.json}") String path) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(path);
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }
}
