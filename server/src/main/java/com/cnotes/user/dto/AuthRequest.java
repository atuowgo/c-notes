package com.cnotes.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注册/登录共用载荷。用户名 3-64,密码 6-128。 */
@Data
public class AuthRequest {
    @NotBlank
    @Size(min = 3, max = 64)
    private String username;
    @NotBlank
    @Size(min = 6, max = 128)
    private String password;
}
