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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        GROUP_MAP.put("appointment", "客户经营");
        GROUP_MAP.put("outing", "客户经营");
        GROUP_MAP.put("workbench", "客户经营");
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
            "lead:page", "resource-pool:list", "lead:create", "lead:claim", "lead:release", "lead:batchClaim",
            "lead:detail", "lead:follow",
            "lead:sensitive:applyView", "lead:sensitive:applyClientView", "lead:sensitive:quota",
            "client:pageLite", "client:tagCounts", "client:detail", "client:update", "client:release", "client:follow", "client:history",
            "client:unassignedPage", "client:claim", "client:batchClaim", "client:lookup",
            "client:createFollow", "client:activityTimeline",
            // 客户画像快照（2026-09-21）：顾问可读本人客户画像并生成新版本；
            // 复核（client:reviewInsight）是管理动作，只给部门经理与全公司角色。
            "client:insight", "client:insightHistory", "client:generateInsight",
            "appointment:pageDay", "appointment:create", "appointment:confirm",
            "appointment:arrive", "appointment:start", "appointment:complete",
            "appointment:markNoShow",
            "appointment:cancel", "appointment:reschedule",
            "appointment:cancelException", "appointment:rescheduleException",
            "outing:pageDay", "outing:create", "outing:depart", "outing:returnFromOuting",
            "outing:uploadPhoto", "outing:photo", "outing:resubmit",
            "workbench:dailyLists",
            "attachment:page",
            "screening:run",
            "notification:mine", "notification:unreadCount", "notification:markAsRead",
            "notification:markAllAsRead", "notification:deleteAll", "notification:deleteBatch",
            "dashboard:todo", "mini:dashboard:stats",
            // 小程序客户链路（D74/D75）：查重 → 建档 → 申请认领/转分配 → 查状态 → 释放本人客户。
            // 注意：这些 api_key 长期缺失，导致顾问/部门经理在小程序调用该链路时被网关拒绝
            // （老板/运营/超管属全量角色，故此前只有他们能用）。
            "mini:client:search", "mini:client:create", "mini:client:claim", "mini:client:claimStatus", "mini:client:release", "mini:lead:releaseLead",
            "mini:client:myClients", "mini:client:seaClients",
            // 站内消息中心（mini 端）：客户角色按 `mini:` 前缀整体放行，员工角色需显式授权，
            // 否则消息接口对其被网关拒绝（2026-09-11 审计 P0-2）。
            "mini:notification:mine", "mini:notification:unreadCount", "mini:notification:readAll",
            "report:overview", "report:operations",
            "report:screeningPage", "report:screeningDetail", "report:screeningAggregate",
            "product:page", "product:get", "product:create",
            "approval:mine", "approval:downloadPage", "approval:downloadApply",
            "auth:me", "auth:logout",
            "dict:listAll",
            "sms:sendCode", "sms:verifyCode",
            "config:status",
            "ocr:fieldDefs", "ocr:recognize",
    };

    /** 主管（DEPT_MANAGER）在顾问基础上追加管理接口 */
    private static final String[] MANAGER_APIS = {
            "org:staffPage", "org:departmentTree",
            "lead:assign", "lead:batchAssign", "lead:batchDelete",
            "client:tagCounts", "client:claim", "client:batchClaim", "client:lookup", "client:assign", "client:batchAssignRoundRobin", "client:recycle", "client:unassignedPage",
            "approval:downloadPage", "approval:downloadApply", "approval:downloadAudit",
            "approval:downloadVoid",
            "approval:allocationPending", "approval:allocationApprove", "approval:allocationReject",
            "approval:unifiedPending", "approval:unifiedCounts", "approval:unifiedAudit",
            "lead:sensitive:pendingApprovals", "lead:sensitive:auditApproval",
            // 员工外出审核（2026-09-21）：主管才可审核本人部门的外出申请，
            // 顾问侧只给提交/打卡/上传照片；老板/运营/超管属全量角色自动放行。
            "outing:pending", "outing:approve", "outing:reject",
            // 客户画像复核：主管及以上可复核本部门/全公司客户的画像快照（禁止自复核）。
            "client:reviewInsight",
            "ocr:fieldDefs", "ocr:saveRecord",
            "product:page", "product:get", "product:create", "product:update", "product:delete",
            "product-city:list", "product-city:page", "product-city:detail", "product-city:batchQuery",
            "product-city:bind", "product-city:update", "product-city:unbind",
            "mini:dashboard:stats",
            "mini:client:search", "mini:client:create", "mini:client:claim", "mini:client:claimStatus", "mini:client:release", "mini:lead:releaseLead",
            "mini:client:myClients", "mini:client:seaClients",
            // 站内消息中心（mini 端）：主管同样需要，避免部门经理看得到入口却调不通
            "mini:notification:mine", "mini:notification:unreadCount", "mini:notification:readAll",
            // 部门经理专属（15-规则 §10/§32/§34）：团队客户视图 + 本团队回收
            "mini:client:teamClients", "mini:client:recycleClient",
            // 资源池管理（2026-10-19）：经理可创建资源池并按池维护回收规则（对齐 tse ADMIN/MANAGER）
            "resource-pool:page", "resource-pool:create", "resource-pool:toggle", "resource-pool:updateRecycle",
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
            verifyApiCoverage();
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
            // 小程序端接口：加 controller 模块段消除同名方法歧义（如 mini:invitation:mine），
            // 避免多个 controller 的 mine() 同时映射到 mini:mine 产生 #1/#2 非确定性后缀。
            if ("mini".equals(mod)) {
                mod = "mini:" + miniModule(handlerMethod);
            }
            String apiKey = mod + ":" + methodName;
            // 敏感查看控制器与 LeadController 同属 lead 模块，固定命名空间避免同名方法
            // 依赖 Spring HandlerMap 的非确定性扫描顺序产生 #1 后缀漂移。
            if (handlerMethod.getBeanType().getSimpleName().equals("SensitiveViewController")) {
                apiKey = "lead:sensitive:" + methodName;
            }
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
                p.setClientTypes(pattern.startsWith("/api/admin/lead/sensitive/") ? "WEB" : "WEB,MINI_APP");
                p.setStatus("ACTIVE");
                p.setRemark(handlerMethod.getMethod().getDeclaringClass().getSimpleName() + "." + methodName);
                p.setCreatedBy("system");
                p.setCreatedAt(LocalDateTime.now());
                toInsert.add(p);
            } else {
                // 敏感查看必须显式收紧存量接口端配置，不能仅修新登记。
                boolean sensitiveWebOnly = pattern.startsWith("/api/admin/lead/sensitive/");
                boolean changed = (sensitiveWebOnly && !"WEB".equals(perm.getClientTypes())) || !httpMethod.equals(perm.getHttpMethod())
                        || !pattern.equals(perm.getPathPattern())
                        || !group.equals(perm.getModuleGroup());
                if (changed) {
                    if (sensitiveWebOnly) perm.setClientTypes("WEB");
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
        // 清理旧格式 mini:<方法名>（单冒号）键，避免与新的 mini:<模块>:<方法名> 并存导致鉴权歧义。
        cleanupLegacyMiniKeys();
        cleanupLegacySensitiveKeys();
        Long total = apiPermissionMapper.selectCount(null);
        return total == null ? 0 : total.intValue();
    }

    /**
     * 从小程序 controller 类名派生模块段（确定性，与扫描顺序无关）。
     *
     * <p>例：MiniInvitationController → invitation；MiniClientController → client；
     * WxJsSdkController → wxjssdk。
     */
    private String miniModule(HandlerMethod handlerMethod) {
        String name = handlerMethod.getBeanType().getSimpleName();
        if (name.endsWith("Controller")) {
            name = name.substring(0, name.length() - "Controller".length());
        }
        if (name.startsWith("Mini")) {
            name = name.substring("Mini".length());
        }
        return name.isEmpty() ? "mini" : name.toLowerCase(Locale.ROOT);
    }

    /**
     * 清理旧格式小程序接口键（mini:&lt;方法名&gt; 单冒号），避免与新的
     * mini:&lt;模块&gt;:&lt;方法名&gt; 双冒号键并存导致网关路径匹配与角色授权歧义。
     * 幂等：仅删除旧格式键；角色授权随后由 backfillRoleApis 按新键补齐。
     */
    private void cleanupLegacyMiniKeys() {
        List<ApiPermission> perms = apiPermissionMapper.selectList(null);
        for (ApiPermission p : perms) {
            if (isLegacyMiniKey(p.getApiKey())) {
                apiPermissionMapper.deleteById(p.getId());
            }
        }
        List<RoleApi> roleApis = roleApiMapper.selectList(null);
        for (RoleApi ra : roleApis) {
            if (isLegacyMiniKey(ra.getApiKey())) {
                roleApiMapper.deleteById(ra.getId());
            }
        }
    }

    /** 将旧 lead:<method> 权限键迁移到稳定敏感命名空间，角色授权随之迁移。 */
    private void cleanupLegacySensitiveKeys() {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("lead:applyView", "lead:sensitive:applyView");
        replacements.put("lead:applyClientView", "lead:sensitive:applyClientView");
        replacements.put("lead:quota", "lead:sensitive:quota");
        replacements.put("lead:pendingApprovals", "lead:sensitive:pendingApprovals");
        replacements.put("lead:auditApproval", "lead:sensitive:auditApproval");
        replacements.put("lead:limitConfig", "lead:sensitive:limitConfig");
        replacements.put("lead:saveLimitConfig", "lead:sensitive:saveLimitConfig");
        for (Map.Entry<String, String> item : replacements.entrySet()) {
            Long replacementCount = apiPermissionMapper.selectCount(new LambdaQueryWrapper<ApiPermission>()
                    .eq(ApiPermission::getApiKey, item.getValue()));
            if (replacementCount == null || replacementCount == 0) continue;
            ApiPermission old = apiPermissionMapper.selectOne(new LambdaQueryWrapper<ApiPermission>()
                    .eq(ApiPermission::getApiKey, item.getKey()));
            // 同名旧权限可能属于其他控制器，不得将其授权移入敏感接口。
            if (old == null || old.getPathPattern() == null
                    || !old.getPathPattern().startsWith("/api/admin/lead/sensitive/")) continue;
            List<RoleApi> grants = roleApiMapper.selectList(new LambdaQueryWrapper<RoleApi>()
                    .eq(RoleApi::getApiKey, item.getKey()));
            for (RoleApi grant : grants) {
                Long exists = roleApiMapper.selectCount(new LambdaQueryWrapper<RoleApi>()
                        .eq(RoleApi::getRoleCode, grant.getRoleCode()).eq(RoleApi::getApiKey, item.getValue()));
                if (exists == null || exists == 0) {
                    RoleApi migrated = new RoleApi(); migrated.setRoleCode(grant.getRoleCode());
                    migrated.setApiKey(item.getValue()); migrated.setCreatedBy("system"); migrated.setCreatedAt(LocalDateTime.now());
                    roleApiMapper.insert(migrated);
                }
                roleApiMapper.deleteById(grant.getId());
            }
            apiPermissionMapper.deleteById(old.getId());
        }
    }

    /** 旧格式判断：mini: 后仅单个方法名（无第二个冒号），如 mini:mine。 */
    private boolean isLegacyMiniKey(String key) {
        return key != null && key.startsWith("mini:") && key.indexOf(':', 5) < 0;
    }

    /**
     * 首次运行默认授权：t_role_api 为空时给 ADVISER / DEPT_MANAGER 插入默认接口。
     */
    public void seedRoleApis() {
        Long cnt = roleApiMapper.selectCount(null);
        if (cnt != null && cnt > 0) {
            return;
        }
        Map<String, List<String>> map = defaultRoleApis(loadActiveApiKeys());
        apiPermissionService.saveRoleApis(map, "system");
        log.info("[ApiPerm] 首次默认授权完成 ADVISER={} DEPT_MANAGER={}",
                map.get("ADVISER").size(), map.get("DEPT_MANAGER").size());
    }

    /**
     * 幂等补齐 DM/ADVISER 默认授权（#198）：不删除既有授权，仅追加缺失的默认接口键。
     * 解决 seedRoleApis 仅在 t_role_api 为空时播种、存量数据无法补齐的问题。
     */
    public void backfillRoleApis() {
        Map<String, List<String>> defaults = defaultRoleApis(loadActiveApiKeys());

        backfillRole("ADVISER", defaults.get("ADVISER"));
        backfillRole("DEPT_MANAGER", defaults.get("DEPT_MANAGER"));
        log.info("[ApiPerm] DM/ADVISER 默认授权补齐完成");
    }

    /**
     * 根据已登记的有效接口构建角色最小默认授权矩阵。
     *
     * <p>只接受精确接口键，禁止按业务模块前缀授权，避免新增高权限接口时被低权限角色自动继承。</p>
     *
     * @param registered 已登记的有效接口键
     * @return ADVISER / DEPT_MANAGER 默认授权
     */
    Map<String, List<String>> defaultRoleApis(Set<String> registered) {
        List<String> adviserKeys = selectRegisteredKeys(registered, ADVISER_APIS);
        List<String> managerKeys = selectRegisteredKeys(registered, MANAGER_APIS);
        for (String key : adviserKeys) {
            if (!managerKeys.contains(key)) {
                managerKeys.add(key);
            }
        }
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("ADVISER", adviserKeys);
        defaults.put("DEPT_MANAGER", managerKeys);
        return defaults;
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

    /** 一次加载全部有效接口键，避免构建角色矩阵时重复查询。 */
    private Set<String> loadActiveApiKeys() {
        return apiPermissionMapper.selectList(
                        new LambdaQueryWrapper<ApiPermission>().eq(ApiPermission::getStatus, "ACTIVE"))
                .stream().map(ApiPermission::getApiKey).collect(Collectors.toSet());
    }

    /** 从已登记接口中选择指定键；用于最小权限授权。 */
    private List<String> selectRegisteredKeys(Set<String> registered, String[] keys) {
        List<String> selected = new ArrayList<>();
        for (String key : keys) {
            if (registered.contains(key) && !selected.contains(key)) {
                selected.add(key);
            }
        }
        return selected;
    }

    /**
     * 校验接口授权覆盖（启动期，仅告警不阻断）。
     *
     * <p>解决「新增 mini 接口漏补员工矩阵授权 → 顾问/经理被网关拒绝」的隐性遗漏：
     * <ol>
     *   <li>悬空引用：ADVISER_APIS / MANAGER_APIS 里引用了已删除/改名的接口键；</li>
     *   <li>未覆盖提示：登记的 {@code mini:*} 接口未纳入员工/经理矩阵，仅靠 CUSTOMER
     *       前缀放行——客户专属接口属正常，但开发者需人工确认是否漏配员工授权。</li>
     * </ol>
     */
    public void verifyApiCoverage() {
        Set<String> registered = loadActiveApiKeys();
        Set<String> adviser = new HashSet<>(Arrays.asList(ADVISER_APIS));
        Set<String> manager = new HashSet<>(Arrays.asList(MANAGER_APIS));

        // 1. 悬空引用：矩阵引用不存在的接口键（删接口未同步清理矩阵）
        List<String> danglingAdviser = adviser.stream()
                .filter(k -> !registered.contains(k)).sorted().collect(Collectors.toList());
        List<String> danglingManager = manager.stream()
                .filter(k -> !registered.contains(k)).sorted().collect(Collectors.toList());
        if (!danglingAdviser.isEmpty()) {
            log.warn("[ApiPerm] 顾问矩阵引用了不存在的接口键（删接口后未清理或键名漂移）：{}", danglingAdviser);
        }
        if (!danglingManager.isEmpty()) {
            log.warn("[ApiPerm] 经理矩阵引用了不存在的接口键（删接口后未清理或键名漂移）：{}", danglingManager);
        }

        // 2. mini 接口未覆盖：提示开发者确认是否漏配员工授权
        Set<String> miniAuthorized = new HashSet<>();
        miniAuthorized.addAll(adviser);
        miniAuthorized.addAll(manager);
        List<String> uncoveredMini = registered.stream()
                .filter(k -> k.startsWith("mini:"))
                .filter(k -> !miniAuthorized.contains(k))
                .sorted().collect(Collectors.toList());
        if (!uncoveredMini.isEmpty()) {
            log.warn("[ApiPerm] 以下 mini 接口未纳入员工/经理矩阵，仅靠 CUSTOMER 前缀放行"
                            + "（客户专属属正常，若需员工访问请补 ADVISER_APIS/MANAGER_APIS）：{}",
                    uncoveredMini);
        }
    }
}
