package com.cnotes.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.user.dto.AuthDto;
import com.cnotes.user.dto.AuthRequest;
import com.cnotes.user.entity.User;
import com.cnotes.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 注册/登录。注册:用户名唯一 + BCrypt 哈希入库,成功即签发 token(自动登录)。
 * 登录:按用户名取行,BCrypt 校验密码,通过则签发 token。密码错抛 BadCredentials → 401。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthDto register(AuthRequest req) {
        if (existsByUsername(req.getUsername())) {
            throw new UsernameExistsException(req.getUsername());
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException dup) {
            // 并发同用户名:唯一索引 uk_username 兜底
            throw new UsernameExistsException(req.getUsername());
        }
        return new AuthDto(jwtService.generate(u.getId(), u.getUsername()), u.getUsername());
    }

    public AuthDto login(AuthRequest req) {
        User u = findByUsername(req.getUsername());
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        return new AuthDto(jwtService.generate(u.getId(), u.getUsername()), u.getUsername());
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(
            Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(
            Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0;
    }
}
