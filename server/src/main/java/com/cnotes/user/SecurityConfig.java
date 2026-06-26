package com.cnotes.user;

import com.cnotes.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 鉴权配置。stateless JWT:AuthFilter 解析 Bearer,授权策略按路径放行/鉴权。
 * cnotes.security.enabled=false 时全放行(测试 profile 复用现有用例,无需每条 mock 鉴权)。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthFilter authFilter;

    @Value("${cnotes.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 基于 UserMapper 的用户详情加载,供 AuthenticationManager 构建 + 退避默认用户自动配置。 */
    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return username -> {
            User u = userService.findByUsername(username);
            if (u == null) {
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
            }
            return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles("USER")
                .build();
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> {
                if (securityEnabled) {
                    a.requestMatchers("/api/auth/**", "/wechat/callback", "/actuator/**", "/error").permitAll()
                     .anyRequest().authenticated();
                } else {
                    a.anyRequest().permitAll();
                }
            })
            .exceptionHandling(e -> e.authenticationEntryPoint((req, resp, ex) -> {
                resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"未登录或令牌已失效\"}");
            }))
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
