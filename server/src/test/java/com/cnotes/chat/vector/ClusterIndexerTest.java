package com.cnotes.chat.vector;

import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3:把每个簇(tag)的演进式综述 living_summary 作为一条 Document 索引进
 * 本地文件型 SimpleVectorStore,metadata 带 tagId/tagName,并落盘。
 * 离线用 stub embedding 断言「落库 + metadata + 落盘 + 去重」;真实语义检索用门控 Ark。
 */
class ClusterIndexerTest {

    private static Tag tag(String id, String name, String summary) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        t.setLivingSummary(summary);
        return t;
    }

    @Test
    void indexesClusterSummariesWithMetadataAndPersists(@TempDir Path tmp) {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel()).build();
        TagMapper tagMapper = mock(TagMapper.class);
        when(tagMapper.selectById("t1")).thenReturn(tag("t1", "Rust", "Rust 所有权与借用的演进式综述。"));
        when(tagMapper.selectById("t2")).thenReturn(tag("t2", "向量库", "向量检索与相似度的演进式综述。"));
        File file = tmp.resolve("vectorstore.json").toFile();
        ClusterIndexer indexer = new ClusterIndexer(store, tagMapper, file.getAbsolutePath());

        indexer.index("t1");
        indexer.index("t2");

        List<Document> hits = store.similaritySearch(SearchRequest.builder().query("任意").topK(10).build());
        assertThat(hits).hasSize(2);
        assertThat(hits).allSatisfy(d -> assertThat(d.getMetadata()).containsKey("tagId"));
        assertThat(hits.stream().map(d -> d.getMetadata().get("tagId")).toList())
            .containsExactlyInAnyOrder("t1", "t2");
        assertThat(file).exists();
    }

    @Test
    void skipsTagWithoutSummary(@TempDir Path tmp) {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel()).build();
        TagMapper tagMapper = mock(TagMapper.class);
        when(tagMapper.selectById("empty")).thenReturn(tag("empty", "空簇", null));
        ClusterIndexer indexer = new ClusterIndexer(store, tagMapper, tmp.resolve("v.json").toFile().getAbsolutePath());

        indexer.index("empty");

        assertThat(store.similaritySearch(SearchRequest.builder().query("x").topK(10).build())).isEmpty();
    }

    @Test
    void reindexingSameTagDoesNotDuplicate(@TempDir Path tmp) {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel()).build();
        TagMapper tagMapper = mock(TagMapper.class);
        ClusterIndexer indexer = new ClusterIndexer(store, tagMapper, tmp.resolve("v.json").toFile().getAbsolutePath());

        when(tagMapper.selectById("t1")).thenReturn(tag("t1", "Rust", "综述 v1"));
        indexer.index("t1");
        when(tagMapper.selectById("t1")).thenReturn(tag("t1", "Rust", "综述 v2 已更新"));
        indexer.index("t1");

        List<Document> hits = store.similaritySearch(SearchRequest.builder().query("x").topK(10).build());
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getText()).contains("v2");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ARK_API_KEY", matches = ".+")
    void realArkRetrievesNearestCluster(@TempDir Path tmp) {
        var props = new com.cnotes.chat.embedding.ArkEmbeddingProperties();
        props.setBaseUrl(System.getenv().getOrDefault("ARK_EMBEDDING_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3"));
        props.setApiKey(System.getenv("ARK_API_KEY"));
        props.setModel(System.getenv().getOrDefault("ARK_EMBEDDING_MODEL", "ep-20260617000458-2mslf"));
        props.setDim(2048);
        EmbeddingModel ark = new com.cnotes.chat.embedding.ArkEmbeddingModel(RestClient.builder(), props);

        SimpleVectorStore store = SimpleVectorStore.builder(ark).build();
        TagMapper tagMapper = mock(TagMapper.class);
        when(tagMapper.selectById("cook")).thenReturn(tag("cook", "烹饪", "如何炖牛肉:火候、调味与时间的家常做法。"));
        when(tagMapper.selectById("space")).thenReturn(tag("space", "航天", "火箭发动机推力与轨道力学的工程概览。"));
        ClusterIndexer indexer = new ClusterIndexer(store, tagMapper, tmp.resolve("v.json").toFile().getAbsolutePath());

        indexer.index("cook");
        indexer.index("space");

        List<Document> hits = store.similaritySearch(
            SearchRequest.builder().query("红烧牛肉怎么做更入味").topK(1).build());
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMetadata().get("tagId")).isEqualTo("cook");
    }

    /** 离线确定性 embedding:维度固定 8,向量由文本哈希派生,使不同综述可被区分检索。 */
    static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            List<String> inst = request.getInstructions();
            for (int i = 0; i < inst.size(); i++) out.add(new Embedding(vec(inst.get(i)), i));
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return vec(document.getText());
        }

        @Override
        public int dimensions() {
            return 8;
        }

        private static float[] vec(String s) {
            float[] v = new float[8];
            int h = s == null ? 0 : s.hashCode();
            for (int i = 0; i < 8; i++) v[i] = ((h >> i) & 1) == 1 ? 1f : 0.1f;
            return v;
        }
    }
}
