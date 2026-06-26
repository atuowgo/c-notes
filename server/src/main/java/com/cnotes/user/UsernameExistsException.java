package com.cnotes.user;

/** 注册时用户名已被占用 → 控制器映射 409。 */
public class UsernameExistsException extends RuntimeException {
    public UsernameExistsException(String username) {
        super("用户名已存在: " + username);
    }
}
