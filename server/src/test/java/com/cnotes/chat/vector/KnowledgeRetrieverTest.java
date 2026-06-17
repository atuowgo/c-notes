package com.cnotes.chat.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 源2 读路径:知识网向量检索。把命中簇的 living_summary + tagId/tagName 映射成 Hit。
 * 离线用确定性 stub embedding(复用 ClusterIndexerTest.StubEmbeddingModel:同文本→同向量)断言
 * 「检索映射 + 空库降级 + 空查询降级」;真实语义召回用门控 Ark。
 */
class KnowledgeRetrieverTest {

    private static SimpleVectorStore seeded(EmbeddingModel em) {
        SimpleVectorStore store = SimpleVectorStore.builder(em).build();
        store.add(List.of(
            Document.builder().id("cook").text("如何炖牛肉:火候、调味与时间的家常做法。")
                .metadata(Map.of("tagId", "cook", "tagName", "烹饪")).build(),
            Document.builder().id("space").text("火箭发动机推力与轨道力学的工程概览。")
                .metadata(Map.of("tagId", "space", "tagName", "航天")).build()
        ));
        return store;
    }

    @Test
    void retrievesHitMappingTagMetadataAndSummary() {
        SimpleVectorStore store = seeded(new ClusterIndexerTest.StubEmbeddingModel());
        KnowledgeRetriever r = new KnowledgeRetriever(store);
        // 用 cook 文档原文作查询 → stub 哈希向量相同 → 确定性命中 cook
        var hits = r.retrieve("如何炖牛肉:火候、调味与时间的家常做法。", 1);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).tagId()).isEqualTo("cook");
        assertThat(hits.get(0).tagName()).isEqualTo("烹饪");
        assertThat(hits.get(0).summary()).contains("炖牛肉");
    }

    @Test
    void emptyStoreDegradesToEmptyList() {
        SimpleVectorStore store = SimpleVectorStore.builder(new ClusterIndexerTest.StubEmbeddingModel()).build();
        KnowledgeRetriever r = new KnowledgeRetriever(store);
        assertThat(r.retrieve("任意问题", 3)).isEmpty();
    }

    @Test
    void blankOrNullQueryReturnsEmpty() {
        SimpleVectorStore store = seeded(new ClusterIndexerTest.StubEmbeddingModel());
        KnowledgeRetriever r = new KnowledgeRetriever(store);
        assertThat(r.retrieve("  ", 3)).isEmpty();
        assertThat(r.retrieve(null, 3)).isEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ARK_API_KEY", matches = ".+")
    void realArkSemanticRetrieveHitsCookCluster() {
        var props = new com.cnotes.chat.embedding.ArkEmbeddingProperties();
        props.setBaseUrl(System.getenv().getOrDefault("ARK_EMBEDDING_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3"));
        props.setApiKey(System.getenv("ARK_API_KEY"));
        props.setModel(System.getenv().getOrDefault("ARK_EMBEDDING_MODEL", "ep-20260617000458-2mslf"));
        props.setDim(2048);
        EmbeddingModel ark = new com.cnotes.chat.embedding.ArkEmbeddingModel(RestClient.builder(), props);
        KnowledgeRetriever r = new KnowledgeRetriever(seeded(ark));

        var hits = r.retrieve("红烧牛肉怎么做更入味", 1);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).tagId()).isEqualTo("cook");
    }
}
