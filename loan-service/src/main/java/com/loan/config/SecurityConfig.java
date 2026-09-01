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
 * 解析 JWT 并填充 {@code UserContext}；鉴权按需在 Controller / 业务层判定。
 * 阶段一最小闭环保持接口可访问（认证过滤器已注入当前用户，业务层后续按需加权限）。
 *
 * @author loan-platform
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 安全过滤链：关闭 CSRF / 会话，注册 JWT 过滤器，全部请求放行（认证由过滤器填充上下文）。
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
                .antMatchers("/api/auth/**", "/api/dict/**", "/api/debug/**").permitAll()
                .anyRequest().permitAll()
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
