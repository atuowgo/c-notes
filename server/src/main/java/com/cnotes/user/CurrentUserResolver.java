package com.cnotes.user;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 SecurityContext 取当前用户 id。
 * AuthFilter 校验通过后把 userId 设为 principal;无认证(匿名 / 测试 permitAll)返回 null。
 * Service 层据此过滤 owner_id:null → 仅匹配 owner_id IS NULL 的历史/无主数据。
 */
@Component
public class CurrentUserResolver {

    public String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal == null ? null : principal.toString();
    }
}
