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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 微信<b>安全模式</b>端到端:配置 token + aes-key + app-id;构造加密的 link 消息 POST →
 * 后端验签(msg_signature)、解密、入库、加密回包;断言回包为密文、解密后含欢迎语,且文章已入库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "wechat.token=cnotes_test_token",
    "wechat.aes-key=abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG",
    "wechat.app-id=wxsecureappid"
})
class WeChatSecureModeApiTest {

    private static final String TOKEN = "cnotes_test_token";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String APP_ID = "wxsecureappid";

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;

    private String msgSign(String ts, String nonce, String encrypt) {
        String[] arr = {TOKEN, ts, nonce, encrypt};
        Arrays.sort(arr);
        return Hashing.sha1Hex(String.join("", arr));
    }

    @Test
    void secureModeDecryptsCollectsAndEncryptsReply() throws Exception {
        WeChatCrypto crypto = new WeChatCrypto(AES_KEY, APP_ID);
        String url = "https://example.com/secure-shared";
        String plainMsg = "<xml><ToUserName><![CDATA[gh]]></ToUserName>"
            + "<FromUserName><![CDATA[user_open_id]]></FromUserName>"
            + "<MsgType><![CDATA[link]]></MsgType>"
            + "<Title><![CDATA[安全模式文章]]></Title>"
            + "<Url><![CDATA[" + url + "]]></Url></xml>";
        String encrypt = crypto.encrypt(plainMsg);

        String ts = "1700001000", nonce = "secnonce";
        String body = "<xml><ToUserName><![CDATA[gh]]></ToUserName>"
            + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";

        MvcResult res = mvc.perform(post("/wechat/callback")
                .param("signature", "ignored-in-secure")
                .param("msg_signature", msgSign(ts, nonce, encrypt))
                .param("timestamp", ts).param("nonce", nonce)
                .param("encrypt_type", "aes")
                .contentType("text/xml").content(body))
           .andExpect(status().isOk())
           .andReturn();

        String responseXml = res.getResponse().getContentAsString();
        assertThat(responseXml).contains("<Encrypt>").contains("<MsgSignature>");

        // 回包密文解出明文 → 含欢迎语(被动回复)。
        String replyCipher = extract(responseXml, "Encrypt");
        String replyPlain = crypto.decrypt(replyCipher);
        assertThat(replyPlain).contains("已收下");

        // 文章真实入库。
        Article a = articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery().eq(Article::getUrlHash, Hashing.md5Hex(url)));
        assertThat(a).isNotNull();
        assertThat(a.getTitle()).isEqualTo("安全模式文章");
        assertThat(a.getSourceType()).isEqualTo("wechat");
    }

    @Test
    void secureModeRejectsBadMsgSignature() throws Exception {
        WeChatCrypto crypto = new WeChatCrypto(AES_KEY, APP_ID);
        String encrypt = crypto.encrypt("<xml><MsgType><![CDATA[text]]></MsgType></xml>");
        String body = "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";

        mvc.perform(post("/wechat/callback")
                .param("msg_signature", "deadbeef")
                .param("timestamp", "1700001001").param("nonce", "n")
                .param("encrypt_type", "aes")
                .contentType("text/xml").content(body))
           .andExpect(status().isForbidden());
    }

    private static String extract(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + "><!\\[CDATA\\[(.*?)]]></" + tag + ">", Pattern.DOTALL)
            .matcher(xml);
        return m.find() ? m.group(1) : "";
    }
}
