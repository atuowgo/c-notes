package com.cnotes.auth.dto;

import com.cnotes.auth.entity.User;

/** 对外暴露的当前用户视图(不含敏感字段)。 */
public record UserDto(String id, String email, String nickname, String avatarUrl, String defaultShareLevel) {
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getNickname(), u.getAvatarUrl(), u.getDefaultShareLevel());
    }
}
