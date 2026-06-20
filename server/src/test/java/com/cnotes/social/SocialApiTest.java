package com.cnotes.social;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多用户阶段 4 端到端验证(真实 HTTP 链路 MockMvc):
 * 点赞 / 评论 / 公开批注 的能力门槛 + 关注 + 通知同步触达。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SocialApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private Cookie login(String handle) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/dev-login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("handle", handle, "nickname", handle))))
            .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("cnotes_token");
    }

    private String myId(Cookie c) throws Exception {
        return om.readTree(mvc.perform(get("/api/auth/me").cookie(c)).andReturn()
            .getResponse().getContentAsString()).get("id").asString();
    }

    private String collect(Cookie c, String url, String title) throws Exception {
        MvcResult r = mvc.perform(post("/api/collect").cookie(c).contentType("application/json")
                .content(om.writeValueAsString(Map.of("url", url, "title", title, "content", "body"))))
            .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private void setLevel(Cookie c, String id, String level) throws Exception {
        Map<String, String> b = new HashMap<>();
        b.put("shareLevel", level);
        mvc.perform(put("/api/articles/" + id + "/share-level").cookie(c)
                .contentType("application/json").content(om.writeValueAsString(b)))
            .andExpect(status().isNoContent());
    }

    @Test
    void likeRespectsGatesAndCountsAndNotifies() throws Exception {
        Cookie alice = login("s4alice");
        Cookie bob = login("s4bob");
        String pub = collect(alice, "https://s4.example/like", "Likeable");
        String priv = collect(alice, "https://s4.example/likepriv", "Private");
        setLevel(alice, pub, "READ_ONLY");

        // 私有 → 404;匿名 → 401
        mvc.perform(post("/api/articles/" + priv + "/like").cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/articles/" + pub + "/like")).andExpect(status().isUnauthorized());

        // bob 点赞 → 公开视图计数 + liked
        mvc.perform(post("/api/articles/" + pub + "/like").cookie(bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/articles/" + pub).cookie(bob))
           .andExpect(jsonPath("$.likeCount").value(1))
           .andExpect(jsonPath("$.liked").value(true));

        // alice 收到 LIKE 通知
        mvc.perform(get("/api/notifications").cookie(alice))
           .andExpect(jsonPath("$[*].type", hasItem("LIKE")));
    }

    @Test
    void commentRequiresCommentableAndThreads() throws Exception {
        Cookie alice = login("s4alice2");
        Cookie bob = login("s4bob2");
        String art = collect(alice, "https://s4.example/comment", "Commentable");

        // READ_ONLY 不可评论 → 403
        setLevel(alice, art, "READ_ONLY");
        mvc.perform(post("/api/articles/" + art + "/comments").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("body", "nope")))).andExpect(status().isForbidden());

        // 提升到 COMMENTABLE
        setLevel(alice, art, "COMMENTABLE");
        MvcResult top = mvc.perform(post("/api/articles/" + art + "/comments").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("body", "讲得好")))).andExpect(status().isOk()).andReturn();
        String topId = om.readTree(top.getResponse().getContentAsString()).get("id").asString();

        // 作者回复 → 楼中楼挂到顶层 + byArticleAuthor
        mvc.perform(post("/api/articles/" + art + "/comments").cookie(alice).contentType("application/json")
                .content(om.writeValueAsString(Map.of("body", "谢谢", "parentId", topId)))).andExpect(status().isOk());

        mvc.perform(get("/api/articles/" + art + "/comments"))
           .andExpect(jsonPath("$", hasSize(2)))
           .andExpect(jsonPath("$[?(@.body=='谢谢')].byArticleAuthor", hasItem(true)))
           .andExpect(jsonPath("$[?(@.body=='谢谢')].parentId", hasItem(topId)));

        // bob 收到 REPLY 通知;alice 收到 COMMENT 通知
        mvc.perform(get("/api/notifications").cookie(bob)).andExpect(jsonPath("$[*].type", hasItem("REPLY")));
        mvc.perform(get("/api/notifications").cookie(alice)).andExpect(jsonPath("$[*].type", hasItem("COMMENT")));
    }

    @Test
    void publicAnnotationRequiresAnnotatable() throws Exception {
        Cookie alice = login("s4alice3");
        Cookie bob = login("s4bob3");
        String art = collect(alice, "https://s4.example/annot", "Annotatable");

        setLevel(alice, art, "COLLECTABLE");   // < ANNOTATABLE
        mvc.perform(post("/api/articles/" + art + "/annotations").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("quote", "body")))).andExpect(status().isForbidden());

        setLevel(alice, art, "ANNOTATABLE");
        mvc.perform(post("/api/articles/" + art + "/annotations").cookie(bob).contentType("application/json")
                .content(om.writeValueAsString(Map.of("quote", "body", "thought", "这里有意思"))))
           .andExpect(status().isOk());

        mvc.perform(get("/api/articles/" + art + "/annotations"))
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].thought").value("这里有意思"))
           .andExpect(jsonPath("$[0].authorNickname").value("s4bob3"));

        mvc.perform(get("/api/notifications").cookie(alice)).andExpect(jsonPath("$[*].type", hasItem("ANNOTATION")));
    }

    @Test
    void followProfileAndFeedAndNotifications() throws Exception {
        Cookie alice = login("s4alice4");
        Cookie bob = login("s4bob4");
        String aliceId = myId(alice);
        String art = collect(alice, "https://s4.example/feed", "Alice Feed Article");
        setLevel(alice, art, "READ_ONLY");

        // 不能关注自己
        mvc.perform(post("/api/users/" + aliceId + "/follow").cookie(alice)).andExpect(status().isBadRequest());

        // bob 关注 alice
        mvc.perform(post("/api/users/" + aliceId + "/follow").cookie(bob)).andExpect(status().isNoContent());

        // alice 主页:粉丝 1;bob 视角 followedByMe=true
        mvc.perform(get("/api/plaza/users/" + aliceId).cookie(bob))
           .andExpect(jsonPath("$.followers").value(1))
           .andExpect(jsonPath("$.followedByMe").value(true));

        // bob 关注流含 alice 的文章
        mvc.perform(get("/api/plaza/following").cookie(bob))
           .andExpect(jsonPath("$[*].title", hasItem("Alice Feed Article")));

        // alice 收到 FOLLOW 通知;未读数 > 0;全标已读后归零
        mvc.perform(get("/api/notifications/unread-count").cookie(alice))
           .andExpect(jsonPath("$.count", greaterThan(0)));
        mvc.perform(get("/api/notifications").cookie(alice)).andExpect(jsonPath("$[*].type", hasItem("FOLLOW")));
        mvc.perform(post("/api/notifications/read").cookie(alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").cookie(alice))
           .andExpect(jsonPath("$.count").value(0));
    }
}
