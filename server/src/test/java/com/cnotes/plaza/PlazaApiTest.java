package com.cnotes.plaza;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多用户阶段 3 端到端验证(真实 HTTP 链路 MockMvc):
 * 发现流可见性(私有排除)+ 质量分排序(高互动靠前)+ 用户公开主页统计 + 分页总数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlazaApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private Cookie login(String handle) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/dev-login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("handle", handle, "nickname", handle))))
            .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("cnotes_token");
    }

    private String aliceId(Cookie alice) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me").cookie(alice)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private String collect(Cookie session, String url, String title) throws Exception {
        MvcResult r = mvc.perform(post("/api/collect").cookie(session).contentType("application/json")
                .content(om.writeValueAsString(Map.of("url", url, "title", title, "content", "body text"))))
            .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private void setLevel(Cookie session, String id, String level) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("shareLevel", level);
        mvc.perform(put("/api/articles/" + id + "/share-level").cookie(session)
                .contentType("application/json").content(om.writeValueAsString(body)))
            .andExpect(status().isNoContent());
    }

    private List<String> titles(String json) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : om.readTree(json)) out.add(n.path("title").asString());
        return out;
    }

    @Test
    void discoverExcludesPrivateAndRanksByQuality() throws Exception {
        Cookie alice = login("p3alice");
        Cookie bob = login("p3bob");
        Cookie carol = login("p3carol");

        String hot = collect(alice, "https://p3.example/hot", "P3 Hot Article");
        String mid = collect(alice, "https://p3.example/mid", "P3 Mid Article");
        String cold = collect(alice, "https://p3.example/cold", "P3 Cold Article");
        String secret = collect(alice, "https://p3.example/secret", "P3 Secret Article");

        setLevel(alice, hot, "COLLECTABLE");
        setLevel(alice, mid, "COLLECTABLE");
        setLevel(alice, cold, "COLLECTABLE");
        // secret 留 PRIVATE(账号默认),不应出现在广场

        // hot:被收录 + 被收藏(行为分最高);mid:仅被收藏;cold:无互动
        mvc.perform(post("/api/articles/" + hot + "/collect").cookie(bob)).andExpect(status().isNoContent());
        mvc.perform(post("/api/articles/" + hot + "/bookmark").cookie(carol)).andExpect(status().isNoContent());
        mvc.perform(post("/api/articles/" + mid + "/bookmark").cookie(bob)).andExpect(status().isNoContent());

        MvcResult r = mvc.perform(get("/api/plaza/discover").param("sort", "score").param("size", "50"))
            .andExpect(status().isOk()).andReturn();
        List<String> ts = titles(r.getResponse().getContentAsString());

        assertThat(ts).contains("P3 Hot Article", "P3 Mid Article", "P3 Cold Article");
        assertThat(ts).doesNotContain("P3 Secret Article");          // 私有不进广场
        assertThat(ts.indexOf("P3 Hot Article")).isLessThan(ts.indexOf("P3 Mid Article"));   // 收录+收藏 > 仅收藏
        assertThat(ts.indexOf("P3 Mid Article")).isLessThan(ts.indexOf("P3 Cold Article"));  // 仅收藏 > 无互动
    }

    @Test
    void publicProfileReflectsStats() throws Exception {
        Cookie alice = login("p3prof");
        Cookie bob = login("p3profbob");
        String aliceId = aliceId(alice);

        String a1 = collect(alice, "https://p3.example/prof1", "Prof One");
        String a2 = collect(alice, "https://p3.example/prof2", "Prof Two");
        setLevel(alice, a1, "COLLECTABLE");
        setLevel(alice, a2, "COLLECTABLE");
        mvc.perform(post("/api/articles/" + a1 + "/collect").cookie(bob)).andExpect(status().isNoContent());
        mvc.perform(post("/api/articles/" + a2 + "/bookmark").cookie(bob)).andExpect(status().isNoContent());

        mvc.perform(get("/api/plaza/users/" + aliceId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nickname").value("p3prof"))
           .andExpect(jsonPath("$.publicCount").value(2))
           .andExpect(jsonPath("$.collectedTotal").value(1))
           .andExpect(jsonPath("$.bookmarkedTotal").value(1));

        // 用户公开文章列表 + 分页总数头
        mvc.perform(get("/api/plaza/users/" + aliceId + "/articles"))
           .andExpect(status().isOk())
           .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void unknownUserProfileIs404() throws Exception {
        mvc.perform(get("/api/plaza/users/nope-nope-nope")).andExpect(status().isNotFound());
    }
}
