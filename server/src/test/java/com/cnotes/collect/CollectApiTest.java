package com.cnotes.collect;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CollectApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired com.cnotes.article.mapper.ArticleMapper articleMapper;

    private String body(String url) throws Exception {
        return om.writeValueAsString(java.util.Map.of("url", url, "title", "t", "content", "hello"));
    }

    @Test
    void collectCreatesPendingArticle() throws Exception {
        mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", hasLength(32)));
    }

    @Test
    void collectStoresDomSnapshotForLevel2Fetch() throws Exception {
        String url = "https://e.com/with-snapshot";
        String payload = om.writeValueAsString(java.util.Map.of(
            "url", url, "title", "t", "content", "短", "domSnapshot", "<html><body>快照正文</body></html>"));
        mvc.perform(post("/api/collect").contentType("application/json").content(payload))
           .andExpect(status().isOk());

        var a = articleMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
            .<com.cnotes.article.entity.Article>lambdaQuery()
            .eq(com.cnotes.article.entity.Article::getUrlHash, com.cnotes.common.Hashing.md5Hex(url)));
        org.assertj.core.api.Assertions.assertThat(a.getDomSnapshot()).contains("快照正文");
    }

    @Test
    void blankUrlReturnsClean400() throws Exception {
        mvc.perform(post("/api/collect").contentType("application/json")
                .content(om.writeValueAsString(java.util.Map.of("url", "", "title", "t"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error", is("validation_failed")))
           .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void collectIsIdempotentByUrl() throws Exception {
        String r1 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(r1).isEqualTo(r2);
    }
}
