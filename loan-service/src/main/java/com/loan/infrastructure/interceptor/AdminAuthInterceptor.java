package com.loan.infrastructure.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.apiperm.service.ApiPermissionService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.org.entity.Menu;
import com.loan.org.entity.RolePermission;
import com.loan.org.mapper.MenuMapper;
import com.loan.org.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端统一鉴权拦截器（阶段3 B1/B2 修复 + T9 码级校验）。
 *
 * <p>拦截 /api/admin/**，四级门禁：
 * <ol>
 *   <li>未登录 → 401；</li>
 *   <li>渠道沙箱白名单（自身菜单树 + 线索录入/本人线索分页 + 本行产品只读分页，T11）；</li>
 *   <li>STAFF 角色级（管理角色 BOSS / DEPT_MANAGER / OPERATOR / SUPER_ADMIN / SUPER）；</li>
 *   <li>码级校验（T9/D28）：受保护业务前缀按 {@code t_role_permission × t_menu.permission_code}
 *       判定页面级权限（page:org / page:blacklist / page:client），与前端 {@code meta.permission} 对齐。
 *       组织域同时包含客户分配所需的员工/部门只读选择器，不能用整个 {@code /org/**} 前缀拦截；
 *       组织与权限写接口改由 {@code AdminRoleGuard} 在 Controller 精确守卫。
 *       说明：audit 前缀因工作台复用（Workbench 调 audit/page 且 ADVISER 无 page:audit）不做码级拦截，
 *       由前端菜单 + meta.permission 管控（审计为只读记录）。</li>
 * </ol>
 *
 * <p>配合 {@code WebMvcConfig} 注册；异常由 {@code GlobalExceptionHandler} 统一转 HTTP 状态。
 *
 * @author loan-platform
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 管理端可访问角色（与 01-角色权限模型、08-矩阵对齐）。
     *  含 ADVISER（顾问）：D19/D20 种子给顾问 7 项本人视角菜单（工作台/线索/客户/材料识别/初筛/工单/报表），
     *  若把顾问排除在管理端外则菜单可见但所有接口 403（D28 修复：交叉评审遗留"顾问被排除出 admin 拦截器"）。 */
    private static final Set<String> ADMIN_ROLES =
            new HashSet<>(Arrays.asList("BOSS", "DEPT_MANAGER", "ADVISER", "OPERATOR", "SUPER_ADMIN", "SUPER"));

    /** 码级校验映射：URI 前缀 → 页面权限码（T9；audit 见类注释，不在此列） */
    private static final java.util.Map<String, String> URI_PERMISSION =
            new java.util.HashMap<>();
    static {
        URI_PERMISSION.put("/api/admin/blacklist/", "page:blacklist");
        URI_PERMISSION.put("/api/admin/client/", "page:client");
    }

    private final MenuMapper menuMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final ApiPermissionService apiPermissionService;

    /** 调试中心生产开关（T17/D30）：默认关闭=生产环境禁用 /api/debug/**（仅开发/联调开启） */
    @org.springframework.beans.factory.annotation.Value("${loan.debug-center.enabled:false}")
    private boolean debugCenterEnabled;

    /** 调试中心可见角色（仅超管两级） */
    private static final Set<String> DEBUG_ROLES =
            new HashSet<>(Arrays.asList("SUPER_ADMIN", "SUPER"));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 非 Controller 映射（静态资源兜底，如打错 URL 落入 /** ResourceHttpRequestHandler）：
        // 不做业务鉴权，直接放行交给容器返回 404。
        // 否则此处抛 BusinessException 时 handler 非 HandlerMethod，@RestControllerAdvice 不生效，
        // 异常冒泡成 HTTP 500（D32 排查：/api/admin/org/menu-tree 错误路径复现）。
        if (!(handler instanceof org.springframework.web.method.HandlerMethod)) {
            return true;
        }
        LoanUser user = UserContext.getUser();
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String uri = request.getRequestURI();
        // 剥离 context-path（/loan），码级校验用不含上下文前缀的路径做前缀匹配（D28 修复：原 startsWith 匹配不上 /loan 前缀）
        String ctx = request.getContextPath();
        String path = uri;
        if (ctx != null && !ctx.isEmpty() && uri != null && uri.startsWith(ctx)) {
            path = uri.substring(ctx.length());
        }
        // 调试中心（T17/D30）：/api/debug/** 生产默认关闭；开启时仅 SUPER_ADMIN/SUPER 可访问
        if (path != null && path.startsWith("/api/debug")) {
            if (!debugCenterEnabled) {
                throw new BusinessException(ResultCode.FORBIDDEN, "调试中心未启用");
            }
            if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())
                    || !DEBUG_ROLES.contains(user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可访问调试中心");
            }
            return true;
        }
        // 渠道专用工作区：接口路径由网关 typeRules(channel:*) 控制，服务内再次校验用户类型；
        // 具体客户/报告数据范围由 ChannelDataScopeService 在列表与详情两层强制收口。
        if (path != null && path.startsWith("/api/channel/")) {
            if (!LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅合作渠道账号可访问");
            }
            return true;
        }
        // 渠道沙箱白名单（T11/D21）：渠道仅可访问 自身菜单树 + 线索录入/本人线索分页 + 本行产品只读分页。
        // 数据隔离在 Controller 层强制（LeadController.page 对 CHANNEL 强制 ownerNo，禁查公海；
        // ProductController.page 对 CHANNEL 强制本行 scope），其余 /api/admin/** 对渠道一律 403。
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType()) && path != null
                && (path.endsWith("/api/admin/org/menu/tree")
                    || path.endsWith("/api/admin/lead")
                    || path.endsWith("/api/admin/lead/page")
                    || (path.endsWith("/api/admin/product/page")
                        && "GET".equalsIgnoreCase(request.getMethod())))) {
            return true;
        }
        // 菜单树接口：任何已登录用户（含渠道）可获取自身角色的菜单树。
        // 渠道沙箱依赖后端按角色过滤返回 3 项菜单（D19），其余 /api/admin/** 仍按下方角色门控。
        if (path != null && path.endsWith("/api/admin/org/menu/tree")) {
            return true;
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅企业员工可访问管理端");
        }
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (!ADMIN_ROLES.contains(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色无权访问管理端");
        }
        // 码级校验（T9/D28）：受保护管理前缀，校验角色是否拥有对应页面权限码
        if (path != null) {
            for (java.util.Map.Entry<String, String> e : URI_PERMISSION.entrySet()) {
                if (path.startsWith(e.getKey())) {
                    checkPagePermission(role, e.getValue());
                    break;
                }
            }
        }
        // 接口级权限（D30 接入）：第三方校验（BOSS 全放；STAFF 查 t_role_api；无授权保守放行）
        ApiPermissionService.CheckResult apiCheck = apiPermissionService.check(user, request.getMethod(), path);
        if (!apiCheck.allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN, "接口无权访问（" + apiCheck.reason + "）");
        }
        return true;
    }

    /**
     * 码级校验：角色在 t_role_permission 中是否授权了带指定 permission_code 的菜单。
     *
     * @param roleCode       角色编码（已大写）
     * @param permissionCode 页面权限码（如 page:org）
     */
    private void checkPagePermission(String roleCode, String permissionCode) {
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getStatus, "ACTIVE")
                .eq(Menu::getPermissionCode, permissionCode));
        if (menus.isEmpty()) {
            // 权限码未配置（种子缺数据时保守放行，避免误伤；配置后即生效）
            return;
        }
        Set<Long> menuIds = menus.stream().map(Menu::getId).collect(java.util.stream.Collectors.toSet());
        Long granted = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleCode, roleCode)
                .in(RolePermission::getMenuId, menuIds));
        if (granted == null || granted <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色无页面访问权限（" + permissionCode + "）");
        }
    }
}
