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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
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
    @MockitoBean com.cnotes.chat.vector.ClusterIndexer clusterIndexer;   // 隔离 Ark 网络
    @Autowired ObjectMapper om;

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

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

    @Test
    void mergeRetagsAndReturnsTarget() throws Exception {
        Tag src = seedClusterWithTwo("m-api-src-" + java.util.UUID.randomUUID());
        Tag tgt = seedClusterWithTwo("m-api-tgt-" + java.util.UUID.randomUUID());

        mvc.perform(post("/api/clusters/merge")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("sourceId", src.getId(), "targetId", tgt.getId()))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", is(tgt.getId())))
           .andExpect(jsonPath("$.articleCount", is(4)));  // 2 + 2 并入

        // 源簇已删 → 404
        mvc.perform(get("/api/clusters/" + src.getId())).andExpect(status().isNotFound());
    }

    @Test
    void mergeSameIdReturns400() throws Exception {
        Tag t = seedClusterWithTwo("m-same-" + java.util.UUID.randomUUID());
        mvc.perform(post("/api/clusters/merge")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("sourceId", t.getId(), "targetId", t.getId()))))
           .andExpect(status().isBadRequest());
    }

    @Test
    void splitCreatesNewCluster() throws Exception {
        Tag src = new Tag(); src.setName("sp-api-src-" + java.util.UUID.randomUUID()); tagMapper.insert(src);
        String a1 = seedDone("sp1"); String a2 = seedDone("sp2"); String a3 = seedDone("sp3");
        for (String aid : new String[]{a1, a2, a3}) {
            ArticleTag t = new ArticleTag(); t.setArticleId(aid); t.setTagId(src.getId());
            articleTagMapper.insert(t);
        }
        String newName = "API新簇-" + java.util.UUID.randomUUID();

        mvc.perform(post("/api/clusters/" + src.getId() + "/split")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("articleIds", List.of(a1, a2), "newTag", newName))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name", is(newName)))
           .andExpect(jsonPath("$.articleCount", is(2)));

        // 源簇剩 1 篇
        mvc.perform(get("/api/clusters/" + src.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.articleCount", is(1)));
    }

    @Test
    void moveRelocatesArticle() throws Exception {
        Tag src = new Tag(); src.setName("mv-api-src-" + java.util.UUID.randomUUID()); tagMapper.insert(src);
        Tag tgt = new Tag(); tgt.setName("mv-api-tgt-" + java.util.UUID.randomUUID()); tagMapper.insert(tgt);
        String a1 = seedDone("mv1"); String a2 = seedDone("mv2");
        for (String aid : new String[]{a1, a2}) {
            ArticleTag t = new ArticleTag(); t.setArticleId(aid); t.setTagId(src.getId());
            articleTagMapper.insert(t);
        }

        mvc.perform(post("/api/clusters/" + src.getId() + "/move")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("articleId", a1, "targetTagId", tgt.getId()))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.articleCount", is(1)));  // 源剩 a2

        mvc.perform(get("/api/clusters/" + tgt.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.articleCount", is(1)));  // 目标得 a1
    }
}
