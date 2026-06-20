package com.cnotes.wechat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 安全模式加解密(WXBizMsgCrypt)纯算法测试:加密→解密往返还原原文;AppID 不匹配应拒绝;
 * EncodingAESKey 非 43 位应拒绝。用一个合法的 43 位 EncodingAESKey 样本(随机 base64)。
 */
class WeChatCryptoTest {

    // 43 位 base64(无 padding)→ 解码 32 字节,合法 EncodingAESKey 样本。
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void encryptThenDecryptRoundTrips() {
        WeChatCrypto crypto = new WeChatCrypto(AES_KEY, "wxappid123");
        String original = "<xml><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[你好 hello]]></Content></xml>";

        String cipher = crypto.encrypt(original);
        assertThat(cipher).isNotBlank().isNotEqualTo(original);

        String decrypted = crypto.decrypt(cipher);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void decryptRejectsWrongAppId() {
        WeChatCrypto writer = new WeChatCrypto(AES_KEY, "appA");
        String cipher = writer.encrypt("<xml>hi</xml>");

        WeChatCrypto reader = new WeChatCrypto(AES_KEY, "appB");   // 期望 appB,实际 appA
        assertThatThrownBy(() -> reader.decrypt(cipher))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyAppIdSkipsAppIdCheck() {
        WeChatCrypto writer = new WeChatCrypto(AES_KEY, "appA");
        String cipher = writer.encrypt("<xml>hi</xml>");
        WeChatCrypto reader = new WeChatCrypto(AES_KEY, "");   // 不校验 appId
        assertThat(reader.decrypt(cipher)).isEqualTo("<xml>hi</xml>");
    }

    @Test
    void rejectsBadKeyLength() {
        assertThatThrownBy(() -> new WeChatCrypto("tooshort", "app"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
