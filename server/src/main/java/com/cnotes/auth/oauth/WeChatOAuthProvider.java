package com.cnotes.auth.oauth;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 微信开放平台「网站应用」扫码登录(qrconnect + snsapi_login 授权码流)。 */
@Component
public class WeChatOAuthProvider implements OAuthProvider {

    private final String appId;
    private final String appSecret;
    private final String redirectUri;

    public WeChatOAuthProvider(
            @Value("${auth.oauth.wechat.app-id:}") String appId,
            @Value("${auth.oauth.wechat.app-secret:}") String appSecret,
            @Value("${auth.oauth.wechat.redirect-uri:}") String redirectUri) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
    }

    @Override public String name() { return "wechat"; }

    @Override public boolean configured() { return !appId.isBlank() && !appSecret.isBlank(); }

    @Override public String authorizeUrl(String state) {
        return "https://open.weixin.qq.com/connect/qrconnect?appid=" + HttpJson.enc(appId)
            + "&redirect_uri=" + HttpJson.enc(redirectUri)
            + "&response_type=code&scope=snsapi_login&state=" + HttpJson.enc(state)
            + "#wechat_redirect";
    }

    @Override public ProviderUser exchange(String code) {
        JsonNode tok = HttpJson.get("https://api.weixin.qq.com/sns/oauth2/access_token?appid="
            + HttpJson.enc(appId) + "&secret=" + HttpJson.enc(appSecret)
            + "&code=" + HttpJson.enc(code) + "&grant_type=authorization_code", Map.of());
        String accessToken = HttpJson.str(tok, "access_token");
        String openid = HttpJson.str(tok, "openid");
        if (accessToken == null || openid == null) throw new IllegalStateException("wechat: no access_token/openid");
        JsonNode u = HttpJson.get("https://api.weixin.qq.com/sns/userinfo?access_token="
            + HttpJson.enc(accessToken) + "&openid=" + HttpJson.enc(openid), Map.of());
        return new ProviderUser("wechat", openid, null,
            HttpJson.strOr(u, "nickname", "微信用户"),
            HttpJson.str(u, "headimgurl"));
    }
}
