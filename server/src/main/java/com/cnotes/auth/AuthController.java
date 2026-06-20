package com.cnotes.auth;

import com.cnotes.auth.dto.UserDto;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.oauth.OAuthProvider;
import com.cnotes.auth.oauth.ProviderUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 认证入口。
 * - 三方授权:GET /login/{provider}(取授权 URL)→ provider 回调 → GET /callback/{provider}(换身份+下发 cookie)。
 * - dev-login:本地/测试用,直接按昵称建会话,使端到端验证无需真实三方凭据(生产置 auth.dev-login.enabled=false)。
 * - /me、/logout。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Duration TTL = Duration.ofDays(30);

    private final JwtService jwt;
    private final AuthService authService;
    private final List<OAuthProvider> providers;

    @Value("${auth.dev-login.enabled:true}")
    private boolean devLoginEnabled;

    @Value("${auth.frontend-url:/}")
    private String frontendUrl;

    /** 当前登录用户;未登录返回 200 + null(前端据此决定是否弹登录)。 */
    @GetMapping("/me")
    public UserDto me() {
        String uid = UserContext.currentRaw();
        if (uid == null) return null;
        User u = authService.get(uid);
        return u == null ? null : UserDto.of(u);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expireCookie().toString())
            .build();
    }

    /** 取三方授权跳转 URL。未配置该渠道返回 503。 */
    @GetMapping("/login/{provider}")
    public ResponseEntity<Map<String, String>> login(@PathVariable String provider,
                                                     @RequestParam(defaultValue = "") String state) {
        OAuthProvider p = find(provider);
        if (p == null || !p.configured()) {
            return ResponseEntity.status(503).body(Map.of("error", "provider_unavailable", "provider", provider));
        }
        return ResponseEntity.ok(Map.of("authorizeUrl", p.authorizeUrl(state)));
    }

    /** 三方回调:换身份 → 建/绑用户 → 下发 cookie → 跳回前端。 */
    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam String code) {
        OAuthProvider p = find(provider);
        if (p == null || !p.configured()) return ResponseEntity.status(503).build();
        ProviderUser pu = p.exchange(code);
        String userId = authService.loginWith(pu);
        return ResponseEntity.status(302)
            .location(URI.create(frontendUrl))
            .header(HttpHeaders.SET_COOKIE, sessionCookie(userId).toString())
            .build();
    }

    /** 本地/测试登录:按 provider=dev + 给定标识建会话。生产关闭。 */
    @PostMapping("/dev-login")
    public ResponseEntity<UserDto> devLogin(@RequestBody Map<String, String> body) {
        if (!devLoginEnabled) return ResponseEntity.status(403).build();
        String handle = body.getOrDefault("handle", "dev");
        ProviderUser pu = new ProviderUser("dev", handle,
            body.get("email"), body.getOrDefault("nickname", handle), null);
        String userId = authService.loginWith(pu);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookie(userId).toString())
            .body(UserDto.of(authService.get(userId)));
    }

    private OAuthProvider find(String name) {
        return providers.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private ResponseCookie sessionCookie(String userId) {
        return ResponseCookie.from(JwtAuthFilter.COOKIE, jwt.issue(userId, TTL))
            .httpOnly(true).path("/").sameSite("Lax").maxAge(TTL).build();
    }

    private ResponseCookie expireCookie() {
        return ResponseCookie.from(JwtAuthFilter.COOKIE, "")
            .httpOnly(true).path("/").sameSite("Lax").maxAge(0).build();
    }
}
