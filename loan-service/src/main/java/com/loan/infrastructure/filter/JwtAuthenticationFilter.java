package com.loan.infrastructure.filter;

import com.loan.auth.service.AuthService;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器（对齐 tse JwtAuthenticationFilter）。
 *
 * <p>解析 Authorization: Bearer token → 校验 JWT → 按 userId 从 Redis 加载完整 User → set UserContext。
 * 白名单（登录/健康检查/RSA 公钥/字典）不强制认证，其余接口依赖 {@link UserContext} 判空决定是否拒绝。
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 无需认证的路径（白名单） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/health",
            "/api/auth/public-key",
            "/api/auth/login",
            "/api/dict/all",
            "/api/mini/auth/login"
    );

    /** 开发态角色模拟请求头 */
    private static final String DEV_ROLE_HEADER = "X-Dev-Role";

    private final JwtService jwtService;
    private final AuthService authService;

    /** 开发态角色模拟开关（默认 false；生产必须关闭） */
    @Value("${loan.dev.role-override.enabled:false}")
    private boolean devRoleOverrideEnabled;

    /** 允许被模拟的角色白名单 */
    @Value("${loan.dev.role-override.allowed:BOSS,DEPT_MANAGER,ADVISER,CHANNEL,CUSTOMER}")
    private String devRoleOverrideAllowed;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (StringUtils.hasText(contextPath)) {
                path = path.substring(contextPath.length());
            }

            // 白名单直接放行
            if (isWhiteListed(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 解析 token → 校验 → 加载会话（成功则滑动续期 2h，活跃用户不被会话过期踢下线）
            String token = extractToken(request);
            if (StringUtils.hasText(token)) {
                Long userId = jwtService.getUserId(token);
                if (userId != null) {
                    LoanUser user = authService.loadSession(userId);
                    if (user != null) {
                        applyDevRoleOverride(request, user);
                        UserContext.setUser(user);
                        authService.renewSession(userId);
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 开发态角色模拟：按请求头 {@code X-Dev-Role} 覆盖当前用户的 userType / roleCode。
     *
     * <p>仅在 {@code loan.dev.role-override.enabled=true} 时生效，且取值必须在白名单
     * {@code loan.dev.role-override.allowed} 内，否则忽略（不抛错，保证联调链路可用）。
     * 生产环境该开关默认关闭，避免越权风险。
     *
     * <p>映射规则：
     * <ul>
     *   <li>BOSS / DEPT_MANAGER / ADVISER → userType=STAFF，roleCode=该值</li>
     *   <li>CHANNEL → userType=CHANNEL，roleCode 置空</li>
     *   <li>CUSTOMER → userType=CUSTOMER，roleCode 置空</li>
     * </ul>
     *
     * @param request 当前请求
     * @param user    已加载的登录用户（原地覆盖角色字段）
     */
    private void applyDevRoleOverride(HttpServletRequest request, LoanUser user) {
        if (!devRoleOverrideEnabled) {
            return;
        }
        String devRole = request.getHeader(DEV_ROLE_HEADER);
        if (!StringUtils.hasText(devRole)) {
            return;
        }
        String role = devRole.trim().toUpperCase();
        if (!parseAllowedRoles().contains(role)) {
            log.warn("[dev-role] 忽略非白名单角色模拟: {}", devRole);
            return;
        }
        if (LoanUser.TYPE_CUSTOMER.equals(role)) {
            user.setUserType(LoanUser.TYPE_CUSTOMER);
            user.setRoleCode(null);
        } else if (LoanUser.TYPE_CHANNEL.equals(role)) {
            user.setUserType(LoanUser.TYPE_CHANNEL);
            user.setRoleCode(null);
        } else {
            user.setUserType(LoanUser.TYPE_STAFF);
            user.setRoleCode(role);
        }
        log.info("[dev-role] 角色模拟生效: {} → userType={}, roleCode={}",
                role, user.getUserType(), user.getRoleCode());
    }

    /**
     * 解析允许被模拟的角色白名单。
     *
     * @return 角色编码列表（大写，无空格）
     */
    private List<String> parseAllowedRoles() {
        if (!StringUtils.hasText(devRoleOverrideAllowed)) {
            return Collections.emptyList();
        }
        String[] parts = devRoleOverrideAllowed.split(",");
        List<String> roles = new ArrayList<String>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                roles.add(part.trim().toUpperCase());
            }
        }
        return roles;
    }

    /**
     * 判断是否白名单路径。
     *
     * @param path 请求路径（去 context-path）
     * @return true 白名单
     */
    private boolean isWhiteListed(String path) {
        for (String white : WHITE_LIST) {
            if (path.equals(white) || path.startsWith(white + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 Bearer token。
     *
     * @param request 请求
     * @return token，不存在返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
