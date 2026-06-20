package com.cnotes.auth.oauth;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GithubOAuthProvider implements OAuthProvider {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GithubOAuthProvider(
            @Value("${auth.oauth.github.client-id:}") String clientId,
            @Value("${auth.oauth.github.client-secret:}") String clientSecret,
            @Value("${auth.oauth.github.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override public String name() { return "github"; }

    @Override public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    @Override public String authorizeUrl(String state) {
        return "https://github.com/login/oauth/authorize?client_id=" + HttpJson.enc(clientId)
            + "&redirect_uri=" + HttpJson.enc(redirectUri)
            + "&scope=" + HttpJson.enc("read:user user:email")
            + "&state=" + HttpJson.enc(state);
    }

    @Override public ProviderUser exchange(String code) {
        JsonNode tok = HttpJson.postForm("https://github.com/login/oauth/access_token",
            Map.of("client_id", clientId, "client_secret", clientSecret,
                   "code", code, "redirect_uri", redirectUri), Map.of());
        String accessToken = HttpJson.str(tok, "access_token");
        if (accessToken == null) throw new IllegalStateException("github: no access_token");
        JsonNode u = HttpJson.get("https://api.github.com/user",
            Map.of("Authorization", "Bearer " + accessToken, "User-Agent", "cnotes"));
        return new ProviderUser("github", HttpJson.str(u, "id"),
            HttpJson.str(u, "email"),
            HttpJson.strOr(u, "name", HttpJson.strOr(u, "login", "github用户")),
            HttpJson.str(u, "avatar_url"));
    }
}
