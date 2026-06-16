package com.cnotes.note;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteApiTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired ObjectMapper om;

    private String seedArticle(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/n/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    private String createNote(String articleId, String quote, String thought) throws Exception {
        String body = om.writeValueAsString(Map.of(
            "articleId", articleId, "quote", quote, "thought", thought,
            "anchor", Map.of("start", 3, "end", 9)));
        String res = mvc.perform(post("/api/notes").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", hasLength(32)))
            .andExpect(jsonPath("$.anchor.start", is(3)))
            .andExpect(jsonPath("$.articleTitle", notNullValue()))
            .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("id").asText();
    }

    @Test
    void createThenListByArticle() throws Exception {
        String articleId = seedArticle("批注测试文章");
        createNote(articleId, "一段被划线的引文", "我的想法");

        mvc.perform(get("/api/notes").param("articleId", articleId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].quote", is("一段被划线的引文")))
           .andExpect(jsonPath("$[0].thought", is("我的想法")))
           .andExpect(jsonPath("$[0].anchor.end", is(9)));
    }

    @Test
    void searchAcrossArticlesByKeyword() throws Exception {
        String a1 = seedArticle("文章甲");
        String a2 = seedArticle("文章乙");
        createNote(a1, "关于 Rust 所有权", "想法一");
        createNote(a2, "另一段引文", "聊到 Rust 的借用检查");

        // q 命中 quote 或 thought,跨文章检索;两条都含 Rust
        mvc.perform(get("/api/notes").param("q", "Rust"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/notes").param("q", "借用检查"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].articleTitle", is("文章乙")));
    }

    @Test
    void updateThoughtThenDelete() throws Exception {
        String articleId = seedArticle("可编辑文章");
        String id = createNote(articleId, "引文", "");

        mvc.perform(put("/api/notes/" + id).contentType("application/json")
                .content(om.writeValueAsString(Map.of("thought", "补写的想法"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.thought", is("补写的想法")));

        mvc.perform(delete("/api/notes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notes").param("articleId", articleId))
           .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        mvc.perform(delete("/api/notes/" + "0".repeat(32))).andExpect(status().isNotFound());
    }
}
