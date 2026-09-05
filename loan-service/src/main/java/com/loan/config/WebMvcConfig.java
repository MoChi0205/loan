package com.loan.config;

import com.loan.infrastructure.interceptor.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册管理端统一鉴权拦截器（阶段3 B1/B2）。
 *
 * <p>拦截 /api/admin/** 全部请求 + /api/debug/**（调试中心，T17 生产开关控制）；
 * 放行管理端登录类接口（如存在 /api/admin/auth/** 时）。
 * 业务接口级权限（归属/角色细分）仍在各 Service/Controller 保留。
 *
 * @author loan-platform
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**", "/api/channel/**", "/api/debug/**")
                .excludePathPatterns(
                        "/api/admin/auth/**",
                        "/api/admin/login",
                        "/api/admin/captcha"
                );
    }
}
