package com.loan.apiperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.apiperm.entity.ApiPermission;
import com.loan.apiperm.entity.RoleApi;
import com.loan.apiperm.mapper.ApiPermissionMapper;
import com.loan.apiperm.mapper.RoleApiMapper;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 接口权限服务：规则构建、Redis 下发（供网关鉴权）、管理端接口清单/角色授权。
 *
 * <p>鉴权规则以 JSON 形式写入 Redis（key {@code loan:api-perm:rules}），网关全局过滤器读取后
 * 按「接口定义（method+pathPattern+clientTypes）× 角色授权」判定；BOSS 为超级角色直接放行。
 * 每次授权变更后调用 {@link #refreshRules(String)} 刷新，保证网关实时生效。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPermissionService {

    /** Redis 鉴权规则 key */
    public static final String RULE_KEY = "loan:api-perm:rules";

    /** 鉴权规则版本 key（网关每请求比对，写后递增） */
    public static final String RULE_VERSION_KEY = "loan:api-perm:version";

    /** 超级角色（全量放行，不落 t_role_api） */
    public static final String SUPER_ROLE = "BOSS";

    private final ApiPermissionMapper apiPermissionMapper;
    private final RoleApiMapper roleApiMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 本地接口权限校验（应用内拦截器用，D30 接入）。
     *
     * <p>缓存 pathPattern → apiKeys 映射（启动时构建，O(1) 匹配），roleCode → apiKey 授权集合
     * （运行期查库，无授权时保守放行防误伤）。
     */
    private final AntPathMatcher antMatcher = new AntPathMatcher();
    /** pathPattern → 该 pattern 命中的所有 apiKey 列表（启动时构建） */
    private List<ApiPermission> pathCache = Collections.emptyList();

    /**
     * 启动时构建 pathPattern → apiKeys 缓存（避免每请求查库）。
     * 角色授权变更（{@link #saveRoleApis}）或接口清单同步后自动 refresh。
     */
    @PostConstruct
    public void initPathCache() {
        rebuildPathCache();
    }

    /**
     * 重建 pathPattern 缓存。
     */
    public void rebuildPathCache() {
        try {
            this.pathCache = apiPermissionMapper.selectList(
                    new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getStatus, "ACTIVE"));
            log.info("[ApiPerm] 本地路径缓存已重建（size={}）", pathCache.size());
        } catch (Exception e) {
            log.warn("[ApiPerm] 路径缓存重建失败，沿用旧缓存", e);
        }
    }

    /**
     * 接口级权限校验结果。
     */
    public static class CheckResult {
        public final boolean allowed;
        public final String reason;
        private CheckResult(boolean allowed, String reason) { this.allowed = allowed; this.reason = reason; }
        public static CheckResult ok(String r) { return new CheckResult(true, r); }
        public static CheckResult deny(String r) { return new CheckResult(false, r); }
    }

    /**
     * 本地接口级权限校验（D30；供 AdminAuthInterceptor 在角色级+码级之后调用）。
     *
     * <p>逻辑：
     * <ol>
     *   <li>未登录或非 /api/admin/** 路径：放行（其他层控制）</li>
     *   <li>BOSS（superRole）：直接放行</li>
     *   <li>CUSTOMER / CHANNEL：放行（由拦截器白名单/typeRules 控制）</li>
     *   <li>STAFF：按 method+pathPattern 匹配 apiKey，查 t_role_api（roleCode, apiKey）
     *       — <b>有授权放行；无授权也放行（保守防误伤，依赖码级 + 角色级兜底）</b>；
     *       业务方补齐 t_role_api 后即生效精确拦截</li>
     *   <li>未匹配到任何 apiKey（接口未登记）：放行（防误伤未登记接口）</li>
     * </ol>
     *
     * @param user   当前用户
     * @param method HTTP 方法
     * @param path   请求路径（不含 context-path）
     * @return 校验结果
     */
    public CheckResult check(LoanUser user, String method, String path) {
        if (user == null || !StringUtils.hasText(path)) {
            return CheckResult.ok("未登录或路径空");
        }
        if (!path.startsWith("/api/admin/")) {
            return CheckResult.ok("非管理端路径（其他层控制）");
        }
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (SUPER_ROLE.equals(role)) {
            return CheckResult.ok("BOSS 超级角色放行");
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            return CheckResult.ok("非员工（白名单/类型规则控制）");
        }
        // STAFF：按 method+path 匹配已登记接口
        List<String> matchedKeys = new java.util.ArrayList<>();
        for (ApiPermission p : pathCache) {
            if (!"ACTIVE".equals(p.getStatus())) continue;
            if (!method.equalsIgnoreCase(p.getHttpMethod())) continue;
            if (antMatcher.match(p.getPathPattern(), path)) {
                matchedKeys.add(p.getApiKey());
            }
        }
        if (matchedKeys.isEmpty()) {
            return CheckResult.ok("接口未登记（保守放行）");
        }
        // STAFF 管理角色默认全量（OPERATOR/SUPER_ADMIN/SUPER）；DM/ADVISER 按 t_role_api 精确授权
        if ("OPERATOR".equals(role) || "SUPER_ADMIN".equals(role) || "SUPER".equals(role)) {
            return CheckResult.ok("管理角色（" + role + "）默认全量 admin 接口放行");
        }
        // 查角色授权
        Long granted = roleApiMapper.selectCount(new LambdaQueryWrapper<RoleApi>()
                .eq(RoleApi::getRoleCode, role)
                .in(RoleApi::getApiKey, matchedKeys));
        if (granted != null && granted > 0) {
            return CheckResult.ok("已配置授权（命中 " + granted + " 条）");
        }
        // 保守放行：D30 方案"无配置不误伤"；若要严格拦截此处改 deny 即可
        return CheckResult.ok("未配置授权（保守放行，待业务方补 t_role_api）");
    }

    /**
     * 构建鉴权规则 JSON（接口清单 + 角色授权 + 超级角色）。
     *
     * @return 规则 Map
     */
    public Map<String, Object> buildRules() {
        List<ApiPermission> apis = apiPermissionMapper.selectList(null);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", String.valueOf(System.currentTimeMillis()));
        root.put("updatedAt", LocalDateTime.now().toString());
        List<Map<String, Object>> apiList = new ArrayList<>();
        for (ApiPermission a : apis) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("apiKey", a.getApiKey());
            m.put("method", a.getHttpMethod());
            m.put("pathPattern", a.getPathPattern());
            m.put("moduleGroup", a.getModuleGroup());
            m.put("clientTypes", splitClientTypes(a.getClientTypes()));
            m.put("status", a.getStatus());
            apiList.add(m);
        }
        root.put("apis", apiList);
        // 角色授权：roleCode -> apiKeys（TreeSet 排序）
        Map<String, Set<String>> roleApis = new LinkedHashMap<>();
        for (RoleApi ra : roleApiMapper.selectList(null)) {
            roleApis.computeIfAbsent(ra.getRoleCode(), k -> new TreeSet<>()).add(ra.getApiKey());
        }
        Map<String, List<String>> roleApisSorted = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : roleApis.entrySet()) {
            roleApisSorted.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        root.put("roleApis", roleApisSorted);
        root.put("superRoles", Arrays.asList(SUPER_ROLE));
        // 用户类型维度（无角色用户：客户只能访问 mini 接口，渠道只能访问 channel 接口）
        Map<String, List<String>> typeRules = new LinkedHashMap<>();
        typeRules.put("CUSTOMER", Arrays.asList("mini:"));
        typeRules.put("CHANNEL", Arrays.asList("channel:"));
        root.put("typeRules", typeRules);
        return root;
    }

    /**
     * 刷新 Redis 鉴权规则（写后网关实时生效）。
     *
     * @param operator 操作人
     */
    public void refreshRules(String operator) {
        try {
            String json = objectMapper.writeValueAsString(buildRules());
            stringRedisTemplate.opsForValue().set(RULE_KEY, json, Duration.ofHours(24));
            stringRedisTemplate.opsForValue().set(RULE_VERSION_KEY, String.valueOf(System.currentTimeMillis()),
                    Duration.ofHours(24));
            log.info("[ApiPerm] 鉴权规则已刷新（operator={}, size={}KB）", operator, json.length() / 1024);
        } catch (Exception e) {
            log.error("[ApiPerm] 刷新鉴权规则失败", e);
        }
    }

    /**
     * 内部规则接口返回（网关 Redis 缺失时兜底拉取）。
     *
     * @return 规则 Map
     */
    public Map<String, Object> internalRules() {
        return buildRules();
    }

    /**
     * 接口清单分页。
     *
     * @param keyword     接口键/路径关键字（可选）
     * @param moduleGroup 分组（可选）
     * @param page        页码
     * @param size        每页大小
     * @return 接口分页
     */
    public PageResult<ApiPermission> page(String keyword, String moduleGroup, int page, int size) {
        LambdaQueryWrapper<ApiPermission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ApiPermission::getApiKey, kw)
                    .or().like(ApiPermission::getPathPattern, kw));
        }
        if (StringUtils.hasText(moduleGroup)) {
            wrapper.eq(ApiPermission::getModuleGroup, moduleGroup);
        }
        wrapper.orderByAsc(ApiPermission::getModuleGroup).orderByAsc(ApiPermission::getApiKey);
        Page<ApiPermission> result = apiPermissionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 角色已授权接口键列表（回显）。
     *
     * @param roleCode 角色编码
     * @return apiKey 列表
     */
    public List<String> listRoleApis(String roleCode) {
        if (SUPER_ROLE.equals(roleCode)) {
            return apiPermissionMapper.selectList(
                            new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getStatus, "ACTIVE"))
                    .stream().map(ApiPermission::getApiKey).collect(Collectors.toList());
        }
        return roleApiMapper.selectList(new LambdaQueryWrapper<RoleApi>()
                        .eq(RoleApi::getRoleCode, roleCode))
                .stream().map(RoleApi::getApiKey).collect(Collectors.toList());
    }

    /**
     * 保存多角色授权（先删后插 + 刷新规则）。
     *
     * @param roleApis 角色 -> apiKey 列表
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleApis(Map<String, List<String>> roleApis, String operator) {
        for (Map.Entry<String, List<String>> e : roleApis.entrySet()) {
            String roleCode = e.getKey();
            List<String> keys = e.getValue() == null ? new ArrayList<>() : e.getValue();
            if (SUPER_ROLE.equals(roleCode)) {
                continue; // BOSS 全量，不落库
            }
            roleApiMapper.delete(new LambdaQueryWrapper<RoleApi>().eq(RoleApi::getRoleCode, roleCode));
            List<String> distinct = keys.stream().distinct().collect(Collectors.toList());
            for (String key : distinct) {
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                RoleApi ra = new RoleApi();
                ra.setRoleCode(roleCode);
                ra.setApiKey(key);
                ra.setCreatedBy(operator == null ? "system" : operator);
                ra.setCreatedAt(LocalDateTime.now());
                roleApiMapper.insert(ra);
            }
        }
        refreshRules(operator == null ? "system" : operator);
    }

    /**
     * 更新接口可用端。
     *
     * @param apiKey      接口键
     * @param clientTypes 端（WEB,MINI_APP 逗号分隔）
     * @param operator    操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateClientTypes(String apiKey, String clientTypes, String operator) {
        ApiPermission perm = apiPermissionMapper.selectOne(
                new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getApiKey, apiKey));
        if (perm == null) {
            return;
        }
        String ct = splitClientTypes(clientTypes).isEmpty() ? "WEB,MINI_APP" : clientTypes;
        perm.setClientTypes(ct);
        perm.setUpdatedBy(operator);
        apiPermissionMapper.updateById(perm);
        refreshRules(operator);
    }

    /**
     * 切分端类型字符串。
     *
     * @param clientTypes 逗号分隔字符串
     * @return 端列表（去重排序）
     */
    public static List<String> splitClientTypes(String clientTypes) {
        if (!StringUtils.hasText(clientTypes)) {
            return new ArrayList<>();
        }
        return Arrays.stream(clientTypes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
