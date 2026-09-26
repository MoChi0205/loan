package com.loan.config;

import com.loan.infrastructure.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Web 安全配置（JWT 认证，对齐 tse SecurityConfig）。
 *
     * <p>策略：Spring Security 关闭 CSRF / 表单登录 / 会话，由 {@link JwtAuthenticationFilter}
     * 解析 JWT 并填充 {@code UserContext}；除明确公开接口外，所有请求必须先完成 JWT 认证。
 *
 * @author loan-platform
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 安全过滤链：关闭 CSRF / 会话，注册 JWT 过滤器；公开接口显式放行，其余请求必须认证。
     *
     * @param http HttpSecurity
     * @return 过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/auth/health", "/api/auth/public-key", "/api/auth/captcha",
                        "/api/auth/code-login", "/api/auth/password-login", "/api/auth/reset-password",
                        "/api/auth/channel-login", "/api/sms/send-code", "/api/sms/verify-code",
                        "/api/dict/all", "/api/mini/auth/login", "/api/mini/auth/phone-login").permitAll()
                // JwtAuthenticationFilter 在进入 Spring Security 授权链前已对非公开接口执行
                // UserContext 认证并返回 401；这里保持 permitAll，避免自定义 UserContext
                // 被 Spring Security 的空 Authentication 再次误判为 403。
                .anyRequest().permitAll()
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
