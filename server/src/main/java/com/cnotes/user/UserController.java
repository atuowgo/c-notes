package com.cnotes.user;

import com.cnotes.user.dto.AuthDto;
import com.cnotes.user.dto.AuthRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权入口:POST /api/auth/register、POST /api/auth/login。
 * 注册成功即签发 token(自动登录);用户名占用 → 409;密码错 → 401。
 * 错误体与 ApiExceptionHandler 同形:{ error, message }。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest req) {
        try {
            AuthDto dto = userService.register(req);
            return ResponseEntity.ok(dto);
        } catch (UsernameExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("username_taken", "用户名已被占用"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        try {
            AuthDto dto = userService.login(req);
            return ResponseEntity.ok(dto);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("bad_credentials", "用户名或密码错误"));
        }
    }

    private Map<String, String> error(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }
}
