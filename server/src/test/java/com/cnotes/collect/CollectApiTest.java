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
    void collectIsIdempotentByUrl() throws Exception {
        String r1 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(r1).isEqualTo(r2);
    }
}
