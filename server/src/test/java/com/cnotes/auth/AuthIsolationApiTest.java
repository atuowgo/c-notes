package com.cnotes.auth;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多用户阶段 1 端到端隔离验证(真实 HTTP 链路 MockMvc):
 * dev-login 下发会话 cookie → 带 cookie 收藏 → 收件箱只见本人文章 → 他人收件箱看不到。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIsolationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private Cookie login(String handle) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/dev-login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("handle", handle, "nickname", handle))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname", is(handle)))
            .andReturn();
        Cookie c = r.getResponse().getCookie(JwtAuthFilter.COOKIE);
        assertThat(c).as("session cookie issued").isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        return c;
    }

    private void collect(Cookie session, String url, String title) throws Exception {
        mvc.perform(post("/api/collect").cookie(session).contentType("application/json")
                .content(om.writeValueAsString(Map.of("url", url, "title", title, "content", "hi"))))
           .andExpect(status().isOk());
    }

    @Test
    void inboxIsIsolatedPerUser() throws Exception {
        Cookie alice = login("alice");
        Cookie bob = login("bob");

        collect(alice, "https://iso.example/alice-secret", "Alice Secret Article");

        // Alice 能看到自己的文章
        mvc.perform(get("/api/articles").cookie(alice))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", hasItem("Alice Secret Article")));

        // Bob 看不到 Alice 的文章
        mvc.perform(get("/api/articles").cookie(bob))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", not(hasItem("Alice Secret Article"))));

        // 匿名(无 cookie → 系统用户)也看不到 Alice 的文章
        mvc.perform(get("/api/articles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", not(hasItem("Alice Secret Article"))));
    }

    @Test
    void sameUrlCollectedByTwoUsersStaysSeparate() throws Exception {
        Cookie alice = login("alice2");
        Cookie bob = login("bob2");
        String url = "https://iso.example/shared-url";

        collect(alice, url, "Alice Copy");
        collect(bob, url, "Bob Copy");

        mvc.perform(get("/api/articles").cookie(alice))
           .andExpect(jsonPath("$[*].title", allOf(hasItem("Alice Copy"), not(hasItem("Bob Copy")))));
        mvc.perform(get("/api/articles").cookie(bob))
           .andExpect(jsonPath("$[*].title", allOf(hasItem("Bob Copy"), not(hasItem("Alice Copy")))));
    }

    @Test
    void meReflectsSessionAndLogoutClears() throws Exception {
        Cookie alice = login("alice3");
        mvc.perform(get("/api/auth/me").cookie(alice))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nickname", is("alice3")));

        // 无 cookie:未登录,me 返回空体
        mvc.perform(get("/api/auth/me"))
           .andExpect(status().isOk())
           .andExpect(content().string(emptyOrNullString()));

        // logout 下发过期 cookie
        mvc.perform(post("/api/auth/logout").cookie(alice))
           .andExpect(status().isNoContent())
           .andExpect(cookie().maxAge(JwtAuthFilter.COOKIE, 0));
    }
}
