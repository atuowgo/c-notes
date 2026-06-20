package com.cnotes.auth.oauth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** 三方授权用的极简 HTTP/JSON 客户端(JDK HttpClient + Jackson)。 */
final class HttpJson {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper OM = new ObjectMapper();

    private HttpJson() {}

    static JsonNode postForm(String url, Map<String, String> form, Map<String, String> headers) {
        String body = form.entrySet().stream()
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .collect(Collectors.joining("&"));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(b::header);
        return send(b.build());
    }

    static JsonNode get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private static JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return OM.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("oauth http failed: " + req.uri(), e);
        }
    }

    static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 取字符串字段;缺失/为 null/为空返回 null。 */
    static String str(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 取字符串字段,缺失则回退 def。 */
    static String strOr(JsonNode node, String field, String def) {
        String s = str(node, field);
        return s == null ? def : s;
    }
}
