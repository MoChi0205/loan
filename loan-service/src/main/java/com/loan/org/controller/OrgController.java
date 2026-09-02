package com.loan.org.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.log.annotation.OpLog;
import com.loan.org.entity.Department;
import com.loan.org.entity.Role;
import com.loan.org.service.OrgService;
import com.loan.org.service.OrgWriteService;
import com.loan.org.vo.MenuNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 组织权限 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/org")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    private final OrgWriteService orgWriteService;

    /**
     * 菜单树（按角色过滤，供侧栏/权限配置用）。
     *
     * <p>普通用户只能获取自身角色菜单：渠道固定 CHANNEL，普通员工固定使用登录态角色，
     * 防止通过 roleCode 参数猜测其他角色菜单。BOSS / SUPER_ADMIN 在组织权限配置页可显式
     * 指定 roleCode 查看目标角色菜单；无登录态不提供兜底角色。
     *
     * @param roleCode 角色编码（可选）
     * @param user     当前登录用户（服务端兜底取角色，防前端空参/越权猜角色）
     * @return 菜单树
     */
    @GetMapping("/menu/tree")
    public Result<List<MenuNodeVO>> menuTree(@RequestParam(required = false) String roleCode,
                                             @CurrentUser LoanUser user) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        String effective;
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            effective = "CHANNEL";
        } else if (LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            effective = user.getRoleCode();
            boolean canInspectOtherRole = "BOSS".equalsIgnoreCase(effective)
                    || "SUPER_ADMIN".equalsIgnoreCase(effective);
            if (canInspectOtherRole && roleCode != null && !roleCode.trim().isEmpty()) {
                effective = roleCode.trim().toUpperCase();
            }
        } else {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户类型不支持管理端菜单");
        }
        if (effective == null || effective.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号未配置角色");
        }
        return Result.ok(orgService.listMenusByRole(effective));
    }

    /**
     * 部门树。
     *
     * @return 部门树
     */
    @GetMapping("/department/tree")
    public Result<List<MenuNodeVO>> departmentTree() {
        return Result.ok(orgService.listDepartmentTree());
    }

    /**
     * 角色列表。
     *
     * @return 角色列表
     */
    @GetMapping("/role/list")
    public Result<List<Role>> roleList() {
        return Result.ok(orgService.listRoles());
    }

    /**
     * 角色已授权菜单（权限配置回显）。
     *
     * @param roleCode 角色编码
     * @return 已授权 menuId 列表
     */
    @GetMapping("/permission/list")
    public Result<List<Long>> permissionList(@RequestParam String roleCode) {
        return Result.ok(orgService.listRolePermissionMenuIds(roleCode));
    }

    /**
     * 员工分页查询。
     *
     * @param deptCode 部门编码（可选，业务编码）
     * @param roleCode 角色编码（可选）
     * @param keyword  关键字（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 员工分页
     */
    @GetMapping("/staff/page")
    public Result<PageResult<Map<String, Object>>> staffPage(
            @RequestParam(required = false) String deptCode,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(orgService.pageStaff(deptCode, roleCode, keyword, page, size, orderBy, orderDir));
    }

    // ============================================================
    // 写接口：部门 / 员工 / 角色权限
    // ============================================================

    /**
     * 新增 / 编辑部门。
     */
    @PostMapping("/department/save")
    @OpLog(bizType = "组织权限", action = "DEPT_SAVE")
    public Result<Void> saveDepartment(@RequestBody Department req, @CurrentUser LoanUser user) {
        orgWriteService.saveDepartment(req, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 停用部门。
     */
    @PostMapping("/department/disable")
    @OpLog(bizType = "组织权限", action = "DEPT_DISABLE")
    public Result<Void> disableDepartment(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        orgWriteService.disableDepartment((String) body.get("deptCode"),
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 新增 / 编辑员工。
     */
    @PostMapping("/staff/save")
    @OpLog(bizType = "组织权限", action = "STAFF_SAVE")
    public Result<Void> saveStaff(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        orgWriteService.saveStaff(body, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 员工离职停用。
     */
    @PostMapping("/staff/disable")
    @OpLog(bizType = "组织权限", action = "STAFF_DISABLE")
    public Result<Void> disableStaff(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        orgWriteService.disableStaff((String) body.get("staffCode"),
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 保存角色权限（先删后插）。
     *
     * @param body { roleCode, menuIds: [], permissionCodes: [] }
     */
    @PostMapping("/permission/save")
    @OpLog(bizType = "组织权限", action = "PERM_SAVE")
    public Result<Void> saveRolePermission(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        List<Long> menuIds = new java.util.ArrayList<>();
        Object rawIds = body.get("menuIds");
        if (rawIds instanceof List) {
            for (Object o : (List<?>) rawIds) {
                if (o instanceof Number) {
                    menuIds.add(((Number) o).longValue());
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<String> permissionCodes = (List<String>) body.get("permissionCodes");
        orgWriteService.saveRolePermission((String) body.get("roleCode"), menuIds, permissionCodes,
                user == null ? "system" : user.getName());
        return Result.ok();
    }
}
