package com.cnotes.cluster;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClusterApiTest {

    @Autowired MockMvc mvc;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleMapper articleMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @MockitoBean ClusterSummarizer summarizer;
    @MockitoBean ClusterSuggester suggester;
    @MockitoBean com.cnotes.chat.vector.ClusterIndexer clusterIndexer;   // 隔离 Ark 网络
    @MockitoBean org.springframework.ai.embedding.EmbeddingModel embeddingModel;  // 隔离 Ark 网络

    private String seedDone(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/ca/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setSummary("摘要"); a.setStatus("done"); a.setKeyPoints("[\"k\"]");
        articleMapper.insert(a);
        return a.getId();
    }

    private Tag seedClusterWithTwo(String name) {
        Tag tag = new Tag(); tag.setName(name); tagMapper.insert(tag);
        for (String aid : new String[]{seedDone("甲"), seedDone("乙")}) {
            ArticleTag t = new ArticleTag(); t.setArticleId(aid); t.setTagId(tag.getId());
            articleTagMapper.insert(t);
        }
        return tag;
    }

    @Test
    void listAndDetailExposeCluster() throws Exception {
        Tag tag = seedClusterWithTwo("API簇-" + java.util.UUID.randomUUID());

        mvc.perform(get("/api/clusters"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].name", hasItem(tag.getName())));

        mvc.perform(get("/api/clusters/" + tag.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name", is(tag.getName())))
           .andExpect(jsonPath("$.articleCount", is(2)))
           .andExpect(jsonPath("$.articles", hasSize(2)));
    }

    @Test
    void regenerateWritesLivingSummary() throws Exception {
        when(summarizer.summarize(any(), any())).thenReturn("演进式综述正文");
        Tag tag = seedClusterWithTwo("综述簇-" + java.util.UUID.randomUUID());

        mvc.perform(post("/api/clusters/" + tag.getId() + "/regenerate"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.livingSummary", is("演进式综述正文")));
    }

    @Test
    void detailMissingReturns404() throws Exception {
        mvc.perform(get("/api/clusters/" + "0".repeat(32))).andExpect(status().isNotFound());
    }

    private String link(String articleId, String tagId) {
        ArticleTag t = new ArticleTag(); t.setArticleId(articleId); t.setTagId(tagId);
        articleTagMapper.insert(t);
        return articleId;
    }

    @Test
    void moveArticleEndpointMovesMembership() throws Exception {
        Tag from = new Tag(); from.setName("移源-" + java.util.UUID.randomUUID()); tagMapper.insert(from);
        Tag to = new Tag(); to.setName("移标-" + java.util.UUID.randomUUID()); tagMapper.insert(to);
        String a = link(seedDone("待移"), from.getId());

        mvc.perform(post("/api/clusters/" + from.getId() + "/move-article")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"articleId\":\"" + a + "\",\"toClusterId\":\"" + to.getId() + "\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", is(to.getId())))
           .andExpect(jsonPath("$.articles[*].id", hasItem(a)));
    }

    @Test
    void mergeEndpointArchivesSourceAndHidesIt() throws Exception {
        Tag from = new Tag(); from.setName("并源-" + java.util.UUID.randomUUID()); tagMapper.insert(from);
        Tag to = new Tag(); to.setName("并标-" + java.util.UUID.randomUUID()); tagMapper.insert(to);
        String a = link(seedDone("并文"), from.getId());

        mvc.perform(post("/api/clusters/merge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromId\":\"" + from.getId() + "\",\"toId\":\"" + to.getId() + "\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", is(to.getId())))
           .andExpect(jsonPath("$.articles[*].id", hasItem(a)));

        // 列表不含归档的源簇
        mvc.perform(get("/api/clusters"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].id", not(hasItem(from.getId()))));
    }

    @Test
    void splitEndpointCreatesNewCluster() throws Exception {
        Tag source = new Tag(); source.setName("拆源-" + java.util.UUID.randomUUID()); tagMapper.insert(source);
        String a1 = link(seedDone("拆甲"), source.getId());
        link(seedDone("拆乙"), source.getId());
        String newName = "拆新-" + java.util.UUID.randomUUID();

        mvc.perform(post("/api/clusters/split")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceId\":\"" + source.getId() + "\",\"name\":\"" + newName
                    + "\",\"articleIds\":[\"" + a1 + "\"]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name", is(newName)))
           .andExpect(jsonPath("$.articles[*].id", hasItem(a1)));
    }

    @Test
    void acceptSuggestionEndpointCreatesCluster() throws Exception {
        String a1 = seedDone("采1"); String a2 = seedDone("采2");
        String name = "采纳-" + java.util.UUID.randomUUID();

        mvc.perform(post("/api/clusters/accept-suggestion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"articleIds\":[\"" + a1 + "\",\"" + a2 + "\"]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name", is(name)))
           .andExpect(jsonPath("$.articles[*].id", hasItem(a1)));
    }

    @Test
    void suggestionsEndpointReturnsOk() throws Exception {
        mvc.perform(get("/api/clusters/suggestions"))
           .andExpect(status().isOk());
    }

    /**
     * 标记法 embed 桩:仅含本测试唯一标记的文章给同一向量(必聚簇),其余按文本散列给互不相近的
     * 高维向量(不会与目标簇混在一起)—— 隔离共享 H2 里其它测试遗留的 done 文章。
     */
    private void stubEmbeddingWithMarker(String marker) {
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            if (text.contains(marker)) return new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
            return hashVector(text);   // 互不相近、与 {1,0,...} 也不相近
        });
    }

    /** 由文本散列出一个 8 维伪随机单位向量;不同文本几乎正交,余弦距离 ~1。 */
    private static float[] hashVector(String s) {
        java.util.Random r = new java.util.Random(s.hashCode());
        float[] v = new float[8];
        v[0] = 0f;                                   // 首维置 0,远离标记向量 {1,0,...}
        for (int i = 1; i < v.length; i++) v[i] = r.nextFloat() + 0.01f;
        return v;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> groupsContaining(String body, String articleId) {
        java.util.List<java.util.Map<String, Object>> all =
            com.jayway.jsonpath.JsonPath.read(body, "$");
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (var g : all) {
            java.util.List<String> ids =
                com.jayway.jsonpath.JsonPath.read(g, "$.articles[*].id");
            if (ids.contains(articleId)) out.add((java.util.Map<String, Object>) g);
        }
        return out;
    }

    @Test
    void vectorSuggestionsClustersSemanticallyCloseArticles() throws Exception {
        String marker = "VEC" + java.util.UUID.randomUUID().toString().replace("-", "");
        String a1 = seedDone(marker + " 向量检索入门");
        String a2 = seedDone(marker + " 向量数据库实践");
        seedDone("园艺与多肉养护无关篇");          // 散列向量 → 噪声
        stubEmbeddingWithMarker(marker);

        String body = mvc.perform(get("/api/clusters/vector-suggestions"))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();

        // 存在一个建议簇同时包含 a1 与 a2(与共享库里其它文章无关,断言只针对我的两篇)。
        var groups = groupsContaining(body, a1);
        assertThat(groups).hasSize(1);
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(groups.get(0), "$.articles[*].id");
        assertThat(ids).contains(a2);
        String name = com.jayway.jsonpath.JsonPath.read(groups.get(0), "$.name");
        assertThat(name).isNotBlank();
    }

    @Test
    void vectorSuggestionsSkipsGroupsAlreadyCoveredByATag() throws Exception {
        String marker = "GNN" + java.util.UUID.randomUUID().toString().replace("-", "");
        String a1 = seedDone(marker + " 图神经网络综述");
        String a2 = seedDone(marker + " 图神经网络应用");
        Tag tag = new Tag(); tag.setName("GNN-" + java.util.UUID.randomUUID()); tagMapper.insert(tag);
        link(a1, tag.getId()); link(a2, tag.getId());
        stubEmbeddingWithMarker(marker);

        String body = mvc.perform(get("/api/clusters/vector-suggestions"))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();

        // a1、a2 已同挂一个受控标签 → 不应作为新建议出现。
        assertThat(groupsContaining(body, a1)).isEmpty();
        assertThat(groupsContaining(body, a2)).isEmpty();
    }
}
