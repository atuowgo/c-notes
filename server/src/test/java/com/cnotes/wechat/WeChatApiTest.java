package com.cnotes.wechat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.common.Hashing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "wechat.token=cnotes_test_token")
class WeChatApiTest {

    private static final String TOKEN = "cnotes_test_token";

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;

    private String sign(String ts, String nonce) {
        String[] arr = {TOKEN, ts, nonce};
        Arrays.sort(arr);
        return Hashing.sha1Hex(String.join("", arr));
    }

    private Article byUrl(String url) {
        return articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery().eq(Article::getUrlHash, Hashing.md5Hex(url)));
    }

    @Test
    void verifyEchoesEchostrOnValidSignature() throws Exception {
        String ts = "1700000000", nonce = "abc123";
        mvc.perform(get("/wechat/callback")
                .param("signature", sign(ts, nonce))
                .param("timestamp", ts).param("nonce", nonce)
                .param("echostr", "hello-wechat"))
           .andExpect(status().isOk())
           .andExpect(content().string("hello-wechat"));
    }

    @Test
    void verifyRejectsBadSignature() throws Exception {
        mvc.perform(get("/wechat/callback")
                .param("signature", "deadbeef")
                .param("timestamp", "1700000000").param("nonce", "abc123")
                .param("echostr", "hello"))
           .andExpect(status().isForbidden());
    }

    @Test
    void textMessageWithUrlCreatesPendingWeChatArticle() throws Exception {
        String ts = "1700000001", nonce = "n1";
        String url = "https://mp.weixin.qq.com/s/forwarded-link-1";
        String xml = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
            + "<FromUserName><![CDATA[user_open_id]]></FromUserName>"
            + "<CreateTime>1700000001</CreateTime>"
            + "<MsgType><![CDATA[text]]></MsgType>"
            + "<Content><![CDATA[读到一篇好文 " + url + " 顺手存一下]]></Content></xml>";

        mvc.perform(post("/wechat/callback")
                .param("signature", sign(ts, nonce)).param("timestamp", ts).param("nonce", nonce)
                .contentType("text/xml").content(xml))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("<MsgType><![CDATA[text]]></MsgType>")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("已收下")));

        Article a = byUrl(url);
        assertThat(a).isNotNull();
        assertThat(a.getSourceType()).isEqualTo("wechat");
        assertThat(a.getStatus()).isEqualTo("pending");
    }

    @Test
    void linkMessageUsesUrlAndTitle() throws Exception {
        String ts = "1700000002", nonce = "n2";
        String url = "https://example.com/shared-article";
        String xml = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
            + "<FromUserName><![CDATA[user_open_id]]></FromUserName>"
            + "<MsgType><![CDATA[link]]></MsgType>"
            + "<Title><![CDATA[分享的文章标题]]></Title>"
            + "<Description><![CDATA[摘要]]></Description>"
            + "<Url><![CDATA[" + url + "]]></Url></xml>";

        mvc.perform(post("/wechat/callback")
                .param("signature", sign(ts, nonce)).param("timestamp", ts).param("nonce", nonce)
                .contentType("text/xml").content(xml))
           .andExpect(status().isOk());

        Article a = byUrl(url);
        assertThat(a).isNotNull();
        assertThat(a.getTitle()).isEqualTo("分享的文章标题");
        assertThat(a.getSourceType()).isEqualTo("wechat");
    }

    @Test
    void textWithoutUrlRepliesPromptAndCreatesNothing() throws Exception {
        String ts = "1700000003", nonce = "n3";
        String xml = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
            + "<FromUserName><![CDATA[user_open_id]]></FromUserName>"
            + "<MsgType><![CDATA[text]]></MsgType>"
            + "<Content><![CDATA[在吗]]></Content></xml>";

        long before = articleMapper.selectCount(null);
        mvc.perform(post("/wechat/callback")
                .param("signature", sign(ts, nonce)).param("timestamp", ts).param("nonce", nonce)
                .contentType("text/xml").content(xml))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("链接")));
        assertThat(articleMapper.selectCount(null)).isEqualTo(before);
    }

    @Test
    void subscribeEventRepliesWelcome() throws Exception {
        String ts = "1700000004", nonce = "n4";
        String xml = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
            + "<FromUserName><![CDATA[new_follower]]></FromUserName>"
            + "<MsgType><![CDATA[event]]></MsgType>"
            + "<Event><![CDATA[subscribe]]></Event></xml>";

        mvc.perform(post("/wechat/callback")
                .param("signature", sign(ts, nonce)).param("timestamp", ts).param("nonce", nonce)
                .contentType("text/xml").content(xml))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("欢迎来到知识炼金炉")));
    }

    @Test
    void unsubscribeEventAcksWithSuccess() throws Exception {
        String ts = "1700000005", nonce = "n5";
        String xml = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
            + "<FromUserName><![CDATA[leaver]]></FromUserName>"
            + "<MsgType><![CDATA[event]]></MsgType>"
            + "<Event><![CDATA[unsubscribe]]></Event></xml>";

        mvc.perform(post("/wechat/callback")
                .param("signature", sign(ts, nonce)).param("timestamp", ts).param("nonce", nonce)
                .contentType("text/xml").content(xml))
           .andExpect(status().isOk())
           .andExpect(content().string("success"));
    }

    @Test
    void postRejectsBadSignature() throws Exception {
        mvc.perform(post("/wechat/callback")
                .param("signature", "bad").param("timestamp", "1").param("nonce", "n")
                .contentType("text/xml").content("<xml/>"))
           .andExpect(status().isForbidden());
    }
}
