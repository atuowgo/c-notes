package com.cnotes.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A1 鉴权端到端。本用例显式开启 cnotes.security.enabled=true(其余用例默认 false 全放行),
 * 覆盖:注册 → 200+token、重复注册 → 409、登录 → 200+token、密码错 → 401、
 * 受保护端点无 token → 401、带 token → 200、校验失败 → 400。
 * 标 @Transactional:同一事务内 MockMvc 可见未提交的注册插入,结束自动回滚不污染共享 H2。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "cnotes.security.enabled=true")
class UserApiTest {

    @Autowired
    MockMvc mvc;

    private String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerLoginThenAccessProtected() throws Exception {
        String username = "u" + System.nanoTime();

        // 注册 → 200 + token(自动登录)
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body(username, "secret123")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token", not(emptyOrNullString())))
           .andExpect(jsonPath("$.username", is(username)));

        // 重复注册 → 409
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body(username, "secret123")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.error", is("username_taken")));

        // 登录 → 200 + token
        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body(username, "secret123")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token", not(emptyOrNullString())))
           .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
            login.getResponse().getContentAsString(), "$.token");

        // 密码错 → 401
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body(username, "wrongpwd")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.error", is("bad_credentials")));

        // 受保护端点无 token → 401
        mvc.perform(get("/api/articles")).andExpect(status().isUnauthorized());

        // 受保护端点带 token → 200(新用户无文章,空列表)
        mvc.perform(get("/api/articles").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());
    }

    @Test
    void blankCredentialsReturn400() throws Exception {
        // username<3 且 password<6 → 校验失败
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"password\":\"x\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error", is("validation_failed")));
    }
}
