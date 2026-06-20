package com.cnotes.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 HttpOnly cookie(cnotes_token)解析 JWT,写入 {@link UserContext}。
 * 无 token / token 失效:不写入,后续按系统初始用户处理(保持单租户存量行为)。
 * Spring Boot 自动注册 OncePerRequestFilter 类型的 Bean 为 servlet 过滤器。
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "cnotes_token";

    private final JwtService jwt;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = readCookie(req);
            jwt.verify(token).ifPresent(UserContext::set);
            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
        }
    }

    private static String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
