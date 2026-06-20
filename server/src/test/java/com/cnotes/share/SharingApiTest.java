package com.cnotes.share;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多用户阶段 2 端到端验证(真实 HTTP 链路 MockMvc):
 * 分享级别覆盖 → 匿名公开只读门槛 → 收藏/收录能力门槛 → 收录卡片归属 + 个人笔记。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SharingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private Cookie login(String handle) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/dev-login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("handle", handle, "nickname", handle))))
            .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("cnotes_token");
    }

    private String collect(Cookie session, String url, String title) throws Exception {
        MvcResult r = mvc.perform(post("/api/collect").cookie(session).contentType("application/json")
                .content(om.writeValueAsString(Map.of("url", url, "title", title, "content", "body text"))))
            .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private void setShareLevel(Cookie session, String articleId, String level) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("shareLevel", level);
        mvc.perform(put("/api/articles/" + articleId + "/share-level").cookie(session)
                .contentType("application/json").content(om.writeValueAsString(body)))
            .andExpect(status().isNoContent());
    }

    @Test
    void privateArticleIsNotPubliclyReadable() throws Exception {
        Cookie alice = login("p2alice");
        String id = collect(alice, "https://p2.example/private", "Private One");
        // 账号默认 PRIVATE 且无覆盖 → 匿名与他人一律 404
        mvc.perform(get("/api/public/articles/" + id)).andExpect(status().isNotFound());
        Cookie bob = login("p2bob");
        mvc.perform(get("/api/public/articles/" + id).cookie(bob)).andExpect(status().isNotFound());
    }

    @Test
    void publishThenAnonymousReadAndOwnerInfo() throws Exception {
        Cookie alice = login("p2alice2");
        String id = collect(alice, "https://p2.example/public", "Public One");
        setShareLevel(alice, id, "READ_ONLY");

        mvc.perform(get("/api/public/articles/" + id))   // 匿名可读
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title", is("Public One")))
           .andExpect(jsonPath("$.ownerNickname", is("p2alice2")))
           .andExpect(jsonPath("$.effectiveShareLevel", is("READ_ONLY")))
           // 公开 DTO 不泄露内部字段
           .andExpect(jsonPath("$.domSnapshot").doesNotExist())
           .andExpect(jsonPath("$.lastError").doesNotExist());
    }

    @Test
    void bookmarkAndCollectRespectLevelGates() throws Exception {
        Cookie alice = login("p2alice3");
        Cookie bob = login("p2bob3");
        String id = collect(alice, "https://p2.example/gated", "Gated One");

        // READ_ONLY:收藏(需 BOOKMARKABLE)与收录(需 COLLECTABLE)都应被拒 403
        setShareLevel(alice, id, "READ_ONLY");
        mvc.perform(post("/api/articles/" + id + "/bookmark").cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(post("/api/articles/" + id + "/collect").cookie(bob)).andExpect(status().isForbidden());

        // BOOKMARKABLE:可收藏,仍不可收录
        setShareLevel(alice, id, "BOOKMARKABLE");
        mvc.perform(post("/api/articles/" + id + "/bookmark").cookie(bob)).andExpect(status().isNoContent());
        mvc.perform(post("/api/articles/" + id + "/collect").cookie(bob)).andExpect(status().isForbidden());

        // COLLECTABLE:可收录
        setShareLevel(alice, id, "COLLECTABLE");
        mvc.perform(post("/api/articles/" + id + "/collect").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("personalNote", "值得一读"))))
           .andExpect(status().isNoContent());

        // bob 公开视图回显互动态
        mvc.perform(get("/api/public/articles/" + id).cookie(bob))
           .andExpect(jsonPath("$.bookmarked", is(true)))
           .andExpect(jsonPath("$.collected", is(true)))
           .andExpect(jsonPath("$.mine", is(false)));

        // bob 收录卡片:归属 alice + 个人笔记
        mvc.perform(get("/api/collections").cookie(bob))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", hasItem("Gated One")))
           .andExpect(jsonPath("$[?(@.title=='Gated One')].collectedFrom", hasItem("p2alice3")))
           .andExpect(jsonPath("$[?(@.title=='Gated One')].personalNote", hasItem("值得一读")));
    }

    @Test
    void cannotCollectOwnArticleAndLoginRequired() throws Exception {
        Cookie alice = login("p2alice4");
        String id = collect(alice, "https://p2.example/own", "Own One");
        setShareLevel(alice, id, "COLLECTABLE");

        // 本人收录自己 → 400
        mvc.perform(post("/api/articles/" + id + "/collect").cookie(alice)).andExpect(status().isBadRequest());

        // 匿名(无 cookie)收藏 → 401
        mvc.perform(post("/api/articles/" + id + "/bookmark")).andExpect(status().isUnauthorized());
    }

    @Test
    void accountDefaultDrivesEffectiveLevelWithoutOverride() throws Exception {
        Cookie alice = login("p2alice5");
        // 账号默认设为 COLLECTABLE
        mvc.perform(put("/api/auth/share-settings").cookie(alice).contentType("application/json")
                .content(om.writeValueAsString(Map.of("defaultShareLevel", "COLLECTABLE"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.defaultShareLevel", is("COLLECTABLE")));

        // 新文章不设逐篇覆盖 → 生效级别继承账号默认
        String id = collect(alice, "https://p2.example/inherit", "Inherited One");
        mvc.perform(get("/api/public/articles/" + id))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.effectiveShareLevel", is("COLLECTABLE")));
    }

    @Test
    void withdrawnSourceShowsPlaceholderButKeepsNote() throws Exception {
        Cookie alice = login("p2alice6");
        Cookie bob = login("p2bob6");
        String id = collect(alice, "https://p2.example/withdraw", "Withdraw One");
        setShareLevel(alice, id, "COLLECTABLE");
        mvc.perform(post("/api/articles/" + id + "/collect").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("personalNote", "我的笔记"))))
           .andExpect(status().isNoContent());

        // alice 转回私有 → bob 收录卡片标记撤回,但保留个人笔记
        setShareLevel(alice, id, "PRIVATE");
        MvcResult r = mvc.perform(get("/api/collections").cookie(bob)).andReturn();
        JsonNode arr = om.readTree(r.getResponse().getContentAsString());
        JsonNode card = null;
        for (JsonNode n : arr) {
            if ("我的笔记".equals(n.path("personalNote").asString())) { card = n; break; }
        }
        assertThat(card).as("withdrawn collection card present").isNotNull();
        assertThat(card.path("sourceWithdrawn").asBoolean()).isTrue();
        assertThat(card.path("articleId").isNull() || card.path("articleId").asString().isEmpty()).isTrue();
    }
}
