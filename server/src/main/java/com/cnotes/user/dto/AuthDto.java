package com.cnotes.user.dto;

/** 注册/登录成功响应:JWT + 用户名(前端存 token 进 localStorage)。 */
public record AuthDto(String token, String username) {}
