package com.loan.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.gateway.auth.ApiRuleService;
import com.loan.gateway.auth.GatewayJwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关全局鉴权过滤器：所有请求先认证后鉴权，通过才转发到下游。
 *
 * <p>流程：白名单放行 → JWT 认证（解析 userId/userType/userNo/roleCode）→
 * 端识别（X-Client-Type，默认 WEB）→ 接口匹配（method + pathPattern）→
 * 三重校验：接口状态 / 可用端 / 角色授权（BOSS 业务全量，系统配置域显式拒绝）。
 * 未登记接口一律拒绝（强制「所有接口都鉴权」）。通过后透传身份头给下游。
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 无需鉴权路径（登录/验证码/公钥/字典/调试） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/loan/api/auth/health",
            "/loan/api/auth/public-key",
            "/loan/api/auth/login",
            "/loan/api/auth/channel-login",
            "/loan/api/mini/auth/login",
            "/loan/api/sms/send-code",
            "/loan/api/dict/all",
            "/loan/api/debug"
    );

    /** 客户端端头 */
    private static final String HEADER_CLIENT_TYPE = "X-Client-Type";
    private static final String CLIENT_WEB = "WEB";
    private static final String CLIENT_MINI = "MINI_APP";

    private final GatewayJwtUtil jwtUtil;
    private final ApiRuleService ruleService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 过滤器主逻辑。
     *
     * @param exchange 当前交换
     * @param chain    过滤链
     * @return 响应
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 1. JWT 认证
        String token = extractToken(exchange.getRequest());
        if (!StringUtils.hasText(token)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 2000, "未登录或会话已过期");
        }
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 2000, "登录已过期，请重新登录");
        }
        // 2. 加载规则并鉴权
        String roleCode = asString(claims.get("roleCode"));
        String userType = asString(claims.get("userType"));
        String userNo = asString(claims.get("userNo"));
        String userId = asString(claims.get("userId"));
        String clientType = resolveClientType(exchange.getRequest(), claims);
        String httpMethod = exchange.getRequest().getMethod() == null
                ? "ALL" : exchange.getRequest().getMethod().name();

        // 当前用户与自身菜单是“已认证即可访问”的公共能力。若继续依赖每个角色重复维护
        // t_role_api，新增角色或权限同步短暂缺项时会出现登录成功却无法读取本人/菜单的情况。
        // 此处仍要求 JWT 有效，并继续向下游透传服务端解析出的身份；具体菜单范围由后端
        // 按当前身份强制收口，不能由普通用户通过 roleCode 参数扩大权限。
        if (isAuthenticatedCommonApi(path, httpMethod)) {
            return forward(exchange, chain, userId, userNo, roleCode, userType, clientType);
        }
        return ruleService.loadRules()
                .flatMap(rules -> {
                    if (rules == null) {
                        return reject(exchange, HttpStatus.FORBIDDEN, 2001, "接口权限规则未配置，请联系管理员");
                    }
                    return doAuthorize(exchange, chain, rules, roleCode, clientType, path,
                            httpMethod,
                            userId, userNo, userType);
                })
                .switchIfEmpty(reject(exchange, HttpStatus.FORBIDDEN, 2001, "接口权限规则不可用，请联系管理员"));
    }

    /**
     * 执行接口鉴权。
     *
     * @param exchange   交换
     * @param chain      过滤链
     * @param rules      规则
     * @param roleCode   角色
     * @param clientType 端
     * @param path       路径
     * @param httpMethod 方法
     * @return 放行或拒绝
     */
    @SuppressWarnings("unchecked")
    private Mono<Void> doAuthorize(ServerWebExchange exchange, GatewayFilterChain chain,
                                   Map<String, Object> rules, String roleCode, String clientType,
                                   String path, String httpMethod, String userId, String userNo, String userType) {
        List<Map<String, Object>> apis = (List<Map<String, Object>>) rules.get("apis");
        // 候选路径：原始路径 + 去掉 context-path 首段（/loan）后的路径
        String[] candidates = {path, stripFirstSegment(path)};
        Map<String, Object> matched = null;
        if (apis != null) {
            // 两轮匹配：先精确模式（不含 { 通配），再通配模式，避免 /order/page 被 /order/{orderNo} 抢占
            for (int round = 0; round < 2 && matched == null; round++) {
                for (Map<String, Object> api : apis) {
                    String pattern = asString(api.get("pathPattern"));
                    String method = asString(api.get("method"));
                    if (!StringUtils.hasText(pattern)) {
                        continue;
                    }
                    boolean wildcard = pattern.contains("{");
                    if (round == 0 && wildcard) {
                        continue;
                    }
                    if (round == 1 && !wildcard) {
                        continue;
                    }
                    boolean methodMatch = "ALL".equals(method) || method.equalsIgnoreCase(httpMethod);
                    if (!methodMatch) {
                        continue;
                    }
                    for (String p : candidates) {
                        if (p != null && pathMatcher.match(pattern, p)) {
                            matched = api;
                            break;
                        }
                    }
                    if (matched != null) {
                        break;
                    }
                }
            }
        }
        // 未登记 → 拒绝（强制全量登记）
        if (matched == null) {
            log.warn("[Gateway] 未登记接口被拦截: {} {}", httpMethod, path);
            return reject(exchange, HttpStatus.FORBIDDEN, 2001, "接口未登记权限配置，请联系管理员");
        }
        // 状态校验
        if ("DISABLED".equals(asString(matched.get("status")))) {
            return reject(exchange, HttpStatus.FORBIDDEN, 2001, "接口已停用");
        }
        // 端校验
        List<String> clientTypes = (List<String>) matched.get("clientTypes");
        log.info("[Gateway] 鉴权决策: path={} clientType={} apiKey={} clientTypes={} role={}",
                path, clientType, matched.get("apiKey"), clientTypes, roleCode);
        if (clientTypes != null && !clientTypes.isEmpty() && !clientTypes.contains(clientType)) {
            log.warn("[Gateway] 端受限: {} 不可访问 {} {} (允许 {})", clientType, httpMethod, path, clientTypes);
            return reject(exchange, HttpStatus.FORBIDDEN, 2001, "该接口不支持 " + clientType + " 端访问");
        }
        String apiKey = asString(matched.get("apiKey"));
        // 显式拒绝优先于超级角色放行：BOSS 是业务全量角色，但不是系统配置管理员。
        if (isRoleExplicitlyDenied(rules, roleCode, apiKey)) {
            log.warn("[Gateway] 角色显式拒绝: role={} {} {} (apiKey={})", roleCode, httpMethod, path, apiKey);
            return reject(exchange, HttpStatus.FORBIDDEN, 2001, "当前角色无权访问系统配置接口");
        }
        // 角色校验（员工：按 t_role_api；无角色用户：按 userType 前缀规则）
        List<String> superRoles = (List<String>) rules.get("superRoles");
        if (superRoles != null && superRoles.contains(roleCode)) {
            return forward(exchange, chain, userId, userNo, roleCode, userType, clientType);
        }
        // 用户类型维度：前缀规则承载完整业务域，精确规则承载跨域的最小必要接口。
        // CHANNEL 不能放开 mini:，否则会同时获得匹配、报告和工单等明确禁止能力。
        Map<String, Object> typeRules = (Map<String, Object>) rules.get("typeRules");
        if (typeRules != null && StringUtils.hasText(userType) && !StringUtils.hasText(roleCode)) {
            Object prefixesObj = typeRules.get(userType);
            if (prefixesObj instanceof List) {
                List<String> prefixes = (List<String>) prefixesObj;
                for (String prefix : prefixes) {
                    if (apiKey.startsWith(prefix)) {
                        return forward(exchange, chain, userId, userNo, roleCode, userType, clientType);
                    }
                }
            }
            Map<String, Object> typeApiRules = (Map<String, Object>) rules.get("typeApiRules");
            if (typeApiRules != null
                    && matchesTypeApiRules(typeApiRules.get(userType), httpMethod, candidates)) {
                return forward(exchange, chain, userId, userNo, roleCode, userType, clientType);
            }
            log.warn("[Gateway] 类型越权拦截: userType={} {} {} (apiKey={})", userType, httpMethod, path, apiKey);
            return reject(exchange, HttpStatus.FORBIDDEN, 2001, "无权限访问该接口");
        }
        Map<String, Object> roleApis = (Map<String, Object>) rules.get("roleApis");
        if (roleApis != null) {
            Object keysObj = roleApis.get(roleCode);
            if (keysObj instanceof List) {
                List<String> keys = (List<String>) keysObj;
                if (keys.contains(apiKey)) {
                    return forward(exchange, chain, userId, userNo, roleCode, userType, clientType);
                }
            }
        }
        log.warn("[Gateway] 越权拦截: role={} {} {} (apiKey={})", roleCode, httpMethod, path, apiKey);
        return reject(exchange, HttpStatus.FORBIDDEN, 2001, "无权限访问该接口");
    }

    /** 先于超级角色放行判断角色显式拒绝前缀，避免 BOSS 越权到系统配置域。 */
    @SuppressWarnings("unchecked")
    boolean isRoleExplicitlyDenied(Map<String, Object> rules, String roleCode, String apiKey) {
        if (rules == null || !StringUtils.hasText(roleCode) || !StringUtils.hasText(apiKey)) {
            return false;
        }
        Object denyMapObject = rules.get("roleDenyApiRules");
        if (!(denyMapObject instanceof Map)) {
            return false;
        }
        Object prefixesObject = ((Map<String, Object>) denyMapObject).get(roleCode);
        if (!(prefixesObject instanceof List)) {
            return false;
        }
        for (Object item : (List<?>) prefixesObject) {
            if (item == null) {
                continue;
            }
            String rule = String.valueOf(item);
            if ((rule.endsWith(":") && apiKey.startsWith(rule)) || apiKey.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匹配无角色用户类型的精确接口授权。
     *
     * <p>该方法保持包级可见，便于对渠道允许/禁止边界做纯单元测试。
     */
    @SuppressWarnings("unchecked")
    boolean matchesTypeApiRules(Object rulesObject, String httpMethod, String[] candidates) {
        if (!(rulesObject instanceof List)) {
            return false;
        }
        for (Object item : (List<?>) rulesObject) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) item;
            String method = asString(rule.get("method"));
            String pattern = asString(rule.get("pathPattern"));
            if (!StringUtils.hasText(pattern)
                    || !("ALL".equalsIgnoreCase(method) || method.equalsIgnoreCase(httpMethod))) {
                continue;
            }
            for (String candidate : candidates) {
                if (candidate != null && pathMatcher.match(pattern, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 放行：透传身份头给下游。
     */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
                               String userId, String userNo, String roleCode, String userType, String clientType) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-No", userNo == null ? "" : userNo)
                .header("X-Role-Code", roleCode == null ? "" : roleCode)
                .header("X-User-Type", userType == null ? "" : userType)
                .header(HEADER_CLIENT_TYPE, clientType)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 拒绝并返回统一 JSON。
     */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * 提取 Bearer token。
     */
    private String extractToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    /**
     * 解析端类型：请求头优先，其次 JWT claim，默认 WEB。
     */
    private String resolveClientType(ServerHttpRequest request, Claims claims) {
        String header = request.getHeaders().getFirst(HEADER_CLIENT_TYPE);
        if (StringUtils.hasText(header)) {
            return header.trim().toUpperCase();
        }
        Object claim = claims.get("clientType");
        if (claim != null) {
            return String.valueOf(claim).toUpperCase();
        }
        return CLIENT_WEB;
    }

    /**
     * 白名单判断。
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
     * 已登录用户公共接口。只豁免角色授权，不豁免 JWT 认证。
     */
    boolean isAuthenticatedCommonApi(String path, String httpMethod) {
        if ("POST".equalsIgnoreCase(httpMethod)) {
            return "/loan/api/auth/logout".equals(path)
                    || "/api/auth/logout".equals(path);
        }
        if (!"GET".equalsIgnoreCase(httpMethod)) {
            return false;
        }
        return "/loan/api/auth/me".equals(path)
                || "/api/auth/me".equals(path)
                || "/loan/api/admin/org/menu/tree".equals(path)
                || "/api/admin/org/menu/tree".equals(path);
    }

    /**
     * 去掉路径首段（context-path 兼容）。
     *
     * @param path 原始路径
     * @return 去掉首段后的路径；不足两段返回 null
     */
    private String stripFirstSegment(String path) {
        if (path == null) {
            return null;
        }
        int idx = path.indexOf('/', 1);
        if (idx < 0) {
            return null;
        }
        String rest = path.substring(idx);
        return rest.length() <= 1 ? null : rest;
    }

    /**
     * 安全转字符串。
     */
    private String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * 优先级：尽早执行。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
