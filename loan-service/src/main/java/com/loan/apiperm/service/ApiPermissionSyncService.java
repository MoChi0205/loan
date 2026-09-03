package com.loan.apiperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.apiperm.entity.ApiPermission;
import com.loan.apiperm.entity.RoleApi;
import com.loan.apiperm.mapper.ApiPermissionMapper;
import com.loan.apiperm.mapper.RoleApiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口权限清单同步器：启动时扫描 Spring 全部 HTTP 映射，自动登记到 t_api_permission。
 *
 * <p>原理：注入 {@link RequestMappingHandlerMapping}，遍历运行时真实映射（HTTP 方法 + 路径模式 + 方法名），
 * 幂等 upsert 到接口权限表。新增 Controller 接口后重启即自动登记，无需手工维护种子数据。
 *
 * <p>首次运行（t_role_api 无数据）时为 DEPT_MANAGER / ADVISER 插入默认授权，
 * 保证系统开箱可用；BOSS 按业务默认全量角色处理，不落库。
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPermissionSyncService implements ApplicationRunner {

    /** 接口分组映射（模块 → 大业务分组，与前端菜单分组一致） */
    private static final Map<String, String> GROUP_MAP = new HashMap<>();

    static {
        GROUP_MAP.put("order", "客户经营");
        GROUP_MAP.put("lead", "客户经营");
        GROUP_MAP.put("screening", "客户经营");
        GROUP_MAP.put("report", "客户经营");
        GROUP_MAP.put("client", "客户经营");
        GROUP_MAP.put("attachment", "客户经营");
        GROUP_MAP.put("notification", "客户经营");
        GROUP_MAP.put("audit", "客户经营");
        GROUP_MAP.put("dashboard", "客户经营");
        GROUP_MAP.put("product", "产品与规则");
        GROUP_MAP.put("rule", "产品与规则");
        GROUP_MAP.put("approval", "运营支撑");
        GROUP_MAP.put("sms", "运营支撑");
        GROUP_MAP.put("reward", "运营支撑");
        GROUP_MAP.put("org", "系统管理");
        GROUP_MAP.put("blacklist", "系统管理");
        GROUP_MAP.put("config", "系统管理");
        GROUP_MAP.put("debug", "系统管理");
        GROUP_MAP.put("dict", "系统管理");
        GROUP_MAP.put("ocr", "系统管理");
        GROUP_MAP.put("auth", "公共");
    }

    /** 顾问（ADVISER）默认可访问接口（一线业务） */
    private static final String[] ADVISER_APIS = {
            "order:page", "order:create", "order:detail", "order:updateStatus",
            "lead:page", "lead:create", "lead:claim", "lead:assign", "lead:applyView", "lead:quota",
            "client:pageLite",
            "attachment:page",
            "screening:run",
            "notification:mine", "notification:unreadCount", "notification:markAsRead",
            "notification:markAllAsRead", "notification:deleteAll", "notification:deleteBatch",
            "dashboard:todo",
            "audit:page", "audit:detail",
            "report:overview", "report:orderTrend", "report:rewardTrend",
            "report:screeningPage", "report:screeningDetail",
            "auth:me", "auth:logout",
            "dict:listAll",
            "sms:sendCode", "sms:verifyCode",
            "config:status",
            "ocr:fieldDefs", "ocr:recognize",
    };

    /** 主管（DEPT_MANAGER）在顾问基础上追加管理接口 */
    private static final String[] MANAGER_APIS = {
            "org:staffPage", "org:departmentTree", "org:roleList", "org:permissionList",
            "approval:productPage", "approval:productDetail", "approval:productAudit",
            "approval:downloadPage", "approval:downloadApply", "approval:downloadAudit",
            "approval:downloadVoid",
            "reward:page", "reward:audit", "reward:voidReward",
            "sms:templatePage", "sms:templateList", "sms:saveTemplate", "sms:toggleTemplate",
            "sms:recordPage", "sms:send",
            "blacklist:page",
            "report:page", "report:save", "report:toggle",
            "ocr:fieldDefs", "ocr:saveRecord",
            "product:page", "product:get", "product:create", "product:update", "product:delete",
            "product-city:list", "product-city:page", "product-city:detail", "product-city:batchQuery",
            "product-city:bind", "product-city:update", "product-city:unbind",
    };

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApiPermissionMapper apiPermissionMapper;
    private final RoleApiMapper roleApiMapper;
    private final ApiPermissionService apiPermissionService;

    /**
     * 应用启动后执行：同步接口清单 + 首次默认授权 + 下发规则到 Redis。
     *
     * @param args 启动参数（未用）
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = syncApis();
            seedRoleApis();
            backfillRoleApis();
            apiPermissionService.refreshRules("system");
            log.info("[ApiPerm] 接口清单同步完成，共 {} 个接口", n);
        } catch (Exception e) {
            log.error("[ApiPerm] 启动同步失败", e);
        }
    }

    /**
     * 扫描 Spring 映射，幂等 upsert 到 t_api_permission。
     *
     * @return 当前接口总数
     */
    public int syncApis() {
        Map<RequestMappingInfo, HandlerMethod> handlers = handlerMapping.getHandlerMethods();
        Map<String, ApiPermission> existing = apiPermissionMapper.selectList(null).stream()
                .collect(Collectors.toMap(ApiPermission::getApiKey, a -> a, (a, b) -> a));

        // 冲突后缀计数
        Map<String, Integer> keyCount = new HashMap<>();
        List<ApiPermission> toUpdate = new ArrayList<>();
        List<ApiPermission> toInsert = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            String cls = handlerMethod.getBeanType().getName();
            // 只登记 com.loan 业务 Controller；跳过调试控制器（网关白名单放行）
            if (!cls.startsWith("com.loan.") || cls.contains("DebugController")) {
                continue;
            }
            Set<String> patterns = new HashSet<>();
            if (info.getPatternsCondition() != null) {
                patterns = info.getPatternsCondition().getPatterns();
            } else if (info.getPathPatternsCondition() != null) {
                patterns = info.getPathPatternsCondition().getPatterns().stream()
                        .map(p -> p.getPatternString())
                        .collect(Collectors.toSet());
            }
            if (patterns.isEmpty()) {
                continue;
            }
            String pattern = patterns.iterator().next();
            if (pattern.contains("/internal/") || pattern.equals("/error")) {
                continue;
            }
            // HTTP 方法（无显式方法 = 全部）
            String httpMethod = "ALL";
            if (info.getMethodsCondition() != null && !info.getMethodsCondition().getMethods().isEmpty()) {
                httpMethod = info.getMethodsCondition().getMethods().iterator().next().name();
            }
            // 模块：/api/admin/order/page -> order；/api/auth/x -> auth
            String[] segs = pattern.split("/");
            String mod = "common";
            for (int i = 0; i < segs.length; i++) {
                if ("api".equals(segs[i])) {
                    if (i + 2 < segs.length && "admin".equals(segs[i + 1])) {
                        mod = segs[i + 2];
                    } else if (i + 1 < segs.length) {
                        mod = segs[i + 1];
                    }
                    break;
                }
            }
            String methodName = handlerMethod.getMethod().getName();
            String apiKey = mod + ":" + methodName;
            Integer cnt = keyCount.getOrDefault(apiKey, 0);
            keyCount.put(apiKey, cnt + 1);
            if (cnt > 0) {
                apiKey = apiKey + "#" + cnt;
            }

            ApiPermission perm = existing.get(apiKey);
            String group = GROUP_MAP.getOrDefault(mod, "公共");
            if (perm == null) {
                ApiPermission p = new ApiPermission();
                p.setApiKey(apiKey);
                p.setHttpMethod(httpMethod);
                p.setPathPattern(pattern);
                p.setModuleGroup(group);
                p.setClientTypes("WEB,MINI_APP");
                p.setStatus("ACTIVE");
                p.setRemark(handlerMethod.getMethod().getDeclaringClass().getSimpleName() + "." + methodName);
                p.setCreatedBy("system");
                p.setCreatedAt(LocalDateTime.now());
                toInsert.add(p);
            } else {
                boolean changed = !httpMethod.equals(perm.getHttpMethod())
                        || !pattern.equals(perm.getPathPattern())
                        || !group.equals(perm.getModuleGroup());
                if (changed) {
                    perm.setHttpMethod(httpMethod);
                    perm.setPathPattern(pattern);
                    perm.setModuleGroup(group);
                    perm.setUpdatedBy("system");
                    toUpdate.add(perm);
                }
            }
        }
        if (!toInsert.isEmpty()) {
            toInsert.forEach(apiPermissionMapper::insert);
        }
        toUpdate.forEach(apiPermissionMapper::updateById);
        Long total = apiPermissionMapper.selectCount(null);
        return total == null ? 0 : total.intValue();
    }

    /**
     * 首次运行默认授权：t_role_api 为空时给 ADVISER / DEPT_MANAGER 插入默认接口。
     */
    public void seedRoleApis() {
        Long cnt = roleApiMapper.selectCount(null);
        if (cnt != null && cnt > 0) {
            return;
        }
        List<String> adviserKeys = new ArrayList<>();
        List<String> managerKeys = new ArrayList<>();
        // 通配：授权按前缀（如 order: 全授权）
        seedByPrefix(adviserKeys, new String[]{"order:", "lead:", "client:", "attachment:", "screening:",
                "notification:", "dashboard:", "audit:", "report:", "auth:", "dict:", "sms:", "config:"});
        addExistingKeys(adviserKeys, ADVISER_APIS);
        seedByPrefix(managerKeys, new String[]{"org:", "approval:", "reward:", "blacklist:", "ocr:"});
        seedByPrefix(managerKeys, new String[]{"product:", "product-city:", "partner-product:", "rule:",
                "rule-template:", "strategy-template:", "execution-plan:", "channel:", "channel-strategy:"});
        managerKeys.addAll(adviserKeys);

        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("ADVISER", adviserKeys);
        map.put("DEPT_MANAGER", managerKeys);
        apiPermissionService.saveRoleApis(map, "system");
        log.info("[ApiPerm] 首次默认授权完成 ADVISER={} DEPT_MANAGER={}", adviserKeys.size(), managerKeys.size());
    }

    /**
     * 幂等补齐 DM/ADVISER 默认授权（#198）：不删除既有授权，仅追加缺失的默认接口键。
     * 解决 seedRoleApis 仅在 t_role_api 为空时播种、存量数据无法补齐的问题。
     */
    public void backfillRoleApis() {
        List<String> adviserKeys = new ArrayList<>();
        List<String> managerKeys = new ArrayList<>();
        seedByPrefix(adviserKeys, new String[]{"order:", "lead:", "client:", "attachment:", "screening:",
                "notification:", "dashboard:", "audit:", "report:", "auth:", "dict:", "sms:", "config:"});
        addExistingKeys(adviserKeys, ADVISER_APIS);
        seedByPrefix(managerKeys, new String[]{"org:", "approval:", "reward:", "blacklist:", "ocr:"});
        seedByPrefix(managerKeys, new String[]{"product:", "product-city:", "partner-product:", "rule:",
                "rule-template:", "strategy-template:", "execution-plan:", "channel:", "channel-strategy:"});
        managerKeys.addAll(adviserKeys);

        backfillRole("ADVISER", adviserKeys);
        backfillRole("DEPT_MANAGER", managerKeys);
        log.info("[ApiPerm] DM/ADVISER 默认授权补齐完成");
    }

    /**
     * 对单一角色幂等追加默认接口授权（仅补缺失，不删既有）。
     *
     * @param roleCode    角色编码
     * @param defaultKeys 该角色应拥有的默认接口键
     */
    private void backfillRole(String roleCode, List<String> defaultKeys) {
        Set<String> existing = roleApiMapper.selectList(
                        new LambdaQueryWrapper<RoleApi>().eq(RoleApi::getRoleCode, roleCode))
                .stream().map(RoleApi::getApiKey).collect(Collectors.toSet());
        List<RoleApi> toAdd = new ArrayList<>();
        for (String key : defaultKeys) {
            if (!existing.contains(key)) {
                RoleApi ra = new RoleApi();
                ra.setRoleCode(roleCode);
                ra.setApiKey(key);
                ra.setCreatedBy("system");
                ra.setCreatedAt(LocalDateTime.now());
                toAdd.add(ra);
            }
        }
        if (!toAdd.isEmpty()) {
            toAdd.forEach(roleApiMapper::insert);
            log.info("[ApiPerm] 角色 {} 补齐 {} 条接口授权", roleCode, toAdd.size());
        }
    }

    /**
     * 按 api_key 前缀收集接口键。
     *
     * @param out    输出集合
     * @param prefix 前缀数组（如 order: / lead:）
     */
    private void seedByPrefix(List<String> out, String[] prefix) {
        Set<String> keys = apiPermissionMapper.selectList(
                        new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getStatus, "ACTIVE"))
                .stream().map(ApiPermission::getApiKey).collect(Collectors.toSet());
        for (String p : prefix) {
            for (String k : keys) {
                if (k.startsWith(p) && !out.contains(k)) {
                    out.add(k);
                }
            }
        }
    }

    /** 从已登记接口中追加指定键；用于不宜整模块放开的最小权限。 */
    private void addExistingKeys(List<String> out, String[] keys) {
        Set<String> registered = apiPermissionMapper.selectList(
                        new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getStatus, "ACTIVE"))
                .stream().map(ApiPermission::getApiKey).collect(Collectors.toSet());
        for (String key : keys) {
            if (registered.contains(key) && !out.contains(key)) {
                out.add(key);
            }
        }
    }
}
