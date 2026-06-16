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
}
