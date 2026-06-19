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
    @MockitoBean ClusterSuggester suggester;
    @MockitoBean com.cnotes.chat.vector.ClusterIndexer clusterIndexer;   // 隔离 Ark 网络

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
}
