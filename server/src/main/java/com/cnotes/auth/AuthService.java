package com.cnotes.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.auth.entity.AuthIdentity;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.AuthIdentityMapper;
import com.cnotes.auth.mapper.UserMapper;
import com.cnotes.auth.oauth.ProviderUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final AuthIdentityMapper identityMapper;

    /**
     * 登录/注册:按 (provider, providerUid) 找已有身份;没有则按 email 绑定到已有用户(三方互通),
     * 仍没有则建新用户。返回 userId。
     */
    @Transactional
    public String loginWith(ProviderUser pu) {
        AuthIdentity existing = identityMapper.selectOne(Wrappers.<AuthIdentity>lambdaQuery()
            .eq(AuthIdentity::getProvider, pu.provider())
            .eq(AuthIdentity::getProviderUid, pu.providerUid()));
        if (existing != null) return existing.getUserId();

        User user = null;
        if (pu.email() != null && !pu.email().isBlank()) {
            user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, pu.email()));
        }
        if (user == null) {
            user = new User();
            user.setEmail(pu.email());
            user.setNickname(pu.nickname());
            user.setAvatarUrl(pu.avatarUrl());
            user.setDefaultShareLevel("PRIVATE");
            userMapper.insert(user);
        }
        AuthIdentity id = new AuthIdentity();
        id.setUserId(user.getId());
        id.setProvider(pu.provider());
        id.setProviderUid(pu.providerUid());
        identityMapper.insert(id);
        return user.getId();
    }

    public User get(String userId) {
        return userId == null ? null : userMapper.selectById(userId);
    }

    /** 更新账号默认分享级别(分享设置);非法值规范化为 PRIVATE。返回更新后的用户。 */
    @Transactional
    public User updateDefaultShareLevel(String userId, String level) {
        User u = userMapper.selectById(userId);
        if (u == null) return null;
        u.setDefaultShareLevel(ShareLevel.parse(level).name());
        userMapper.updateById(u);
        return u;
    }
}
