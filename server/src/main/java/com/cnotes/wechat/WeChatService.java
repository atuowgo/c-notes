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
 * <p>当前支持<b>明文模式</b>(公众号后台「服务器配置」选明文/兼容模式):签名校验用 token。
 * 安全模式(AES 消息加解密)留作后续硬化。
 */
@Service
@RequiredArgsConstructor
public class WeChatService {

    private final CollectService collectService;

    @Value("${wechat.token:}")
    private String token;

    @Value("${wechat.welcome-reply:已收下,正在自动整理 ✅ 处理完可在阅读端查看。}")
    private String welcomeReply;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<\"']+");

    /** 校验微信服务器签名:sha1(sort(token,timestamp,nonce))。 */
    public boolean verify(String signature, String timestamp, String nonce) {
        if (signature == null || token == null || token.isBlank()) return false;
        String[] arr = {token, timestamp == null ? "" : timestamp, nonce == null ? "" : nonce};
        Arrays.sort(arr);
        return Hashing.sha1Hex(String.join("", arr)).equals(signature);
    }

    /** 处理一条消息,返回被动回复 XML。 */
    public String handle(String xmlBody) {
        Map<String, String> msg = parse(xmlBody);
        String fromUser = msg.getOrDefault("FromUserName", "");
        String toUser = msg.getOrDefault("ToUserName", "");
        String type = msg.getOrDefault("MsgType", "");

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
