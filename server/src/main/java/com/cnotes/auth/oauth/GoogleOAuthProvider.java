package com.cnotes.auth.oauth;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GoogleOAuthProvider implements OAuthProvider {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthProvider(
            @Value("${auth.oauth.google.client-id:}") String clientId,
            @Value("${auth.oauth.google.client-secret:}") String clientSecret,
            @Value("${auth.oauth.google.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override public String name() { return "google"; }

    @Override public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    @Override public String authorizeUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + HttpJson.enc(clientId)
            + "&redirect_uri=" + HttpJson.enc(redirectUri)
            + "&response_type=code&scope=" + HttpJson.enc("openid email profile")
            + "&state=" + HttpJson.enc(state);
    }

    @Override public ProviderUser exchange(String code) {
        JsonNode tok = HttpJson.postForm("https://oauth2.googleapis.com/token",
            Map.of("client_id", clientId, "client_secret", clientSecret, "code", code,
                   "grant_type", "authorization_code", "redirect_uri", redirectUri), Map.of());
        String accessToken = HttpJson.str(tok, "access_token");
        if (accessToken == null) throw new IllegalStateException("google: no access_token");
        JsonNode u = HttpJson.get("https://www.googleapis.com/oauth2/v3/userinfo",
            Map.of("Authorization", "Bearer " + accessToken));
        return new ProviderUser("google", HttpJson.str(u, "sub"),
            HttpJson.str(u, "email"),
            HttpJson.strOr(u, "name", "Google用户"),
            HttpJson.str(u, "picture"));
    }
}
