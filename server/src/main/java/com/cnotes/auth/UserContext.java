package com.cnotes.auth;

import com.cnotes.auth.entity.User;

/**
 * 每请求的当前用户上下文(ThreadLocal)。由 {@link JwtAuthFilter} 写入、请求结束清理。
 * 未带有效 token 的请求回退为系统初始用户(承接单租户存量数据),保证既有单租户行为不破。
 */
public final class UserContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    public static void set(String userId) { CURRENT.set(userId); }

    public static void clear() { CURRENT.remove(); }

    /** 当前用户 id;未登录(无 token)时回退到系统初始用户。永不返回 null。 */
    public static String currentOrSystem() {
        String v = CURRENT.get();
        return v == null ? User.SYSTEM_ID : v;
    }

    /** 原始当前用户 id,可能为 null(用于判断是否真正登录)。 */
    public static String currentRaw() { return CURRENT.get(); }
}
