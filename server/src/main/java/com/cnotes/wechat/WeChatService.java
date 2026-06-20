package com.cnotes.wechat;

import com.cnotes.collect.CollectService;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.common.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信公众号收集(V2):用户把文章链接转发/发送给公众号 → 回调到此 →
 * 复用 {@link CollectService} 写入同一张 article 表(source_type=wechat,先入库后处理)→
 * 立即被动回复,满足微信 5 秒响应限制。
 *
 * <p>支持<b>明文模式</b>(签名校验用 token)与<b>安全模式</b>(AES 消息加解密):配置了
 * {@code wechat.aes-key}(43 位 EncodingAESKey)即开启安全模式 —— POST 体里的 {@code <Encrypt>}
 * 经 {@link WeChatCrypto} 解出真实消息,回复同样加密回包,签名用 msg_signature 校验。
 * 还支持<b>关注/事件消息</b>:用户关注公众号(subscribe)自动欢迎,其余事件静默 ack。
 */
@Service
@RequiredArgsConstructor
public class WeChatService {

    private final CollectService collectService;

    @Value("${wechat.token:}")
    private String token;

    @Value("${wechat.welcome-reply:已收下,正在自动整理 ✅ 处理完可在阅读端查看。}")
    private String welcomeReply;

    @Value("${wechat.subscribe-reply:欢迎来到知识炼金炉 ✨ 把文章链接转发或发给我,我帮你自动收下、提炼、织进知识网。}")
    private String subscribeReply;

    /** 安全模式:43 位 EncodingAESKey;留空则明文模式。 */
    @Value("${wechat.aes-key:}")
    private String aesKey;

    /** 公众号 AppID,安全模式下解密后校验来源。 */
    @Value("${wechat.app-id:}")
    private String appId;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<\"']+");

    /** 静默 ack:WeChat 收到此串即视为「无需回复」。 */
    private static final String ACK = "success";

    /** 安全模式加解密器,首次用到时按配置惰性构建;未配置 aes-key 则为 null(明文模式)。 */
    private volatile WeChatCrypto crypto;

    private WeChatCrypto crypto() {
        if (aesKey == null || aesKey.isBlank()) return null;
        WeChatCrypto c = crypto;
        if (c == null) {
            synchronized (this) {
                c = crypto;
                if (c == null) crypto = c = new WeChatCrypto(aesKey, appId);
            }
        }
        return c;
    }

    public boolean secureModeEnabled() {
        return aesKey != null && !aesKey.isBlank();
    }

    /** 校验微信服务器签名:sha1(sort(token,timestamp,nonce))。 */
    public boolean verify(String signature, String timestamp, String nonce) {
        if (signature == null || token == null || token.isBlank()) return false;
        String[] arr = {token, timestamp == null ? "" : timestamp, nonce == null ? "" : nonce};
        Arrays.sort(arr);
        return Hashing.sha1Hex(String.join("", arr)).equals(signature);
    }

    /**
     * 安全模式入口:验签(msg_signature 含 Encrypt)→ 解密 → 走明文处理 → 把回复加密回包。
     * 返回 null 表示验签失败(控制器据此 403);返回 "success" 表示静默 ack(无需加密)。
     */
    public String handleEncrypted(String body, String msgSignature, String timestamp, String nonce) {
        WeChatCrypto c = crypto();
        if (c == null) return handle(body);   // 未开安全模式则按明文处理(兼容)

        String encrypt = parse(body).get("Encrypt");
        if (encrypt == null || encrypt.isBlank()) return null;
        if (!verifyMsgSignature(msgSignature, timestamp, nonce, encrypt)) return null;

        String plainXml;
        try {
            plainXml = c.decrypt(encrypt);
        } catch (Exception e) {
            return null;   // 解密/AppID 校验失败
        }
        String reply = handle(plainXml);
        if (reply == null || reply.isBlank() || ACK.equals(reply)) return ACK;   // 事件 ack 不加密
        return encryptReply(c, reply, timestamp, nonce);
    }

    /** 处理一条(明文)消息,返回被动回复 XML;事件按需欢迎或静默 ack("success")。 */
    public String handle(String xmlBody) {
        Map<String, String> msg = parse(xmlBody);
        String fromUser = msg.getOrDefault("FromUserName", "");
        String toUser = msg.getOrDefault("ToUserName", "");
        String type = msg.getOrDefault("MsgType", "");

        // 关注/事件消息:关注欢迎,其余(取关、菜单点击等)静默 ack。
        if ("event".equals(type)) {
            String event = msg.getOrDefault("Event", "");
            if ("subscribe".equalsIgnoreCase(event)) {
                return buildTextReply(fromUser, toUser, subscribeReply);
            }
            return ACK;
        }

        String url = null;
        String title = null;
        if ("link".equals(type)) {
            url = msg.get("Url");
            title = msg.get("Title");
        } else if ("text".equals(type)) {
            url = firstUrl(msg.getOrDefault("Content", ""));
        }

        String reply;
        if (url != null && !url.isBlank()) {
            CollectRequest req = new CollectRequest();
            req.setUrl(url.trim());
            req.setTitle(title);
            req.setSourceType("wechat");
            collectService.collect(req);
            reply = welcomeReply;
        } else if ("text".equals(type) || "link".equals(type)) {
            reply = "把文章链接发给我就行,我会自动收下并整理。";
        } else {
            reply = "你好,把文章链接转发或发给我,我帮你收进知识炼金炉。";
        }
        return buildTextReply(fromUser, toUser, reply);
    }

    /** 安全模式消息签名:sha1(sort(token, timestamp, nonce, encrypt))。 */
    public boolean verifyMsgSignature(String msgSignature, String timestamp, String nonce, String encrypt) {
        if (msgSignature == null || token == null || token.isBlank()) return false;
        String[] arr = {token, nz(timestamp), nz(nonce), nz(encrypt)};
        Arrays.sort(arr);
        return Hashing.sha1Hex(String.join("", arr)).equals(msgSignature);
    }

    /** 把明文回复 XML 加密并包成安全模式回包(<Encrypt>+<MsgSignature>+<TimeStamp>+<Nonce>)。 */
    private String encryptReply(WeChatCrypto c, String replyXml, String timestamp, String nonce) {
        String encrypt = c.encrypt(replyXml);
        String ts = nz(timestamp).isBlank() ? String.valueOf(System.currentTimeMillis() / 1000) : timestamp;
        String n = nz(nonce).isBlank() ? Long.toHexString(System.nanoTime()) : nonce;
        String[] arr = {token, ts, n, encrypt};
        Arrays.sort(arr);
        String msgSig = Hashing.sha1Hex(String.join("", arr));
        return "<xml>"
            + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>"
            + "<MsgSignature><![CDATA[" + msgSig + "]]></MsgSignature>"
            + "<TimeStamp>" + ts + "</TimeStamp>"
            + "<Nonce><![CDATA[" + n + "]]></Nonce>"
            + "</xml>";
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private String firstUrl(String text) {
        Matcher m = URL.matcher(text == null ? "" : text);
        return m.find() ? m.group() : null;
    }

    /** 被动回复:收件人=原发送用户(from),发件人=公众号(to)。 */
    private String buildTextReply(String originalFrom, String originalTo, String content) {
        return "<xml>"
            + "<ToUserName><![CDATA[" + originalFrom + "]]></ToUserName>"
            + "<FromUserName><![CDATA[" + originalTo + "]]></FromUserName>"
            + "<CreateTime>" + (System.currentTimeMillis() / 1000) + "</CreateTime>"
            + "<MsgType><![CDATA[text]]></MsgType>"
            + "<Content><![CDATA[" + content + "]]></Content>"
            + "</xml>";
    }

    /** 解析微信消息 XML 的一级子节点为 map;禁用外部实体防 XXE。 */
    private Map<String, String> parse(String xml) {
        Map<String, String> map = new HashMap<>();
        if (xml == null || xml.isBlank()) return map;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new InputSource(new StringReader(xml)));
            NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    map.put(n.getNodeName(), n.getTextContent().trim());
                }
            }
        } catch (Exception e) {
            // 解析失败返回空 map,上层回退为引导提示
        }
        return map;
    }
}
