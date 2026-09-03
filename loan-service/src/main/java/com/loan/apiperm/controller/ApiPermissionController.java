package com.loan.apiperm.controller;

import com.loan.api.dto.PageResult;
import com.loan.apiperm.entity.ApiPermission;
import com.loan.apiperm.service.ApiPermissionService;
import com.loan.apiperm.service.ApiPermissionSyncService;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AdminRoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 接口权限管理（Web 管理端）+ 内部规则下发（供网关鉴权）。
 *
 * <p>管理接口仅运营管理员或超级管理员可操作；内部规则接口校验
 * {@code X-Internal-Token} 头（网关兜底拉取）。
 *
 * @author loan-platform
 */
@RestController
@RequiredArgsConstructor
public class ApiPermissionController {

    private final ApiPermissionService apiPermissionService;
    private final ApiPermissionSyncService syncService;

    /** 内部接口调用令牌（网关配置同值） */
    @Value("${internal.api.token:loan-internal-token}")
    private String internalToken;

    /**
     * 接口清单分页（仅系统配置管理员）。
     *
     * @param keyword     接口键/路径关键字（可选）
     * @param moduleGroup 分组（可选）
     * @param page        页码
     * @param size        每页大小
     * @param user        当前用户
     * @return 接口分页
     */
    @GetMapping("/api/admin/api-perm/page")
    public Result<PageResult<ApiPermission>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "moduleGroup", required = false) String moduleGroup,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        AdminRoleGuard.requireSystemConfigAdmin(user);
        return Result.ok(apiPermissionService.page(keyword, moduleGroup, page, size));
    }

    /**
     * 角色已授权接口列表（回显）。
     *
     * @param roleCode 角色编码
     * @return apiKey 列表
     */
    @GetMapping("/api/admin/api-perm/role/list")
    public Result<List<String>> roleList(
            @RequestParam(value = "roleCode") String roleCode, @CurrentUser LoanUser user) {
        AdminRoleGuard.requireSystemConfigAdmin(user);
        return Result.ok(apiPermissionService.listRoleApis(roleCode));
    }

    /**
     * 保存角色接口授权。
     *
     * @param body {roleCode, apiKeys:[...]}
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/api/admin/api-perm/role/save")
    public Result<Void> roleSave(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        AdminRoleGuard.requireSystemConfigAdmin(user);
        String roleCode = (String) body.get("roleCode");
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色编码必填");
        }
        @SuppressWarnings("unchecked")
        List<String> apiKeys = (List<String>) body.get("apiKeys");
        Map<String, List<String>> roleApis = new java.util.LinkedHashMap<>();
        roleApis.put(roleCode, apiKeys == null ? new ArrayList<>() : apiKeys);
        apiPermissionService.saveRoleApis(roleApis, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 更新接口可用端（WEB / MINI_APP）。
     *
     * @param body {apiKey, clientTypes}
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/api/admin/api-perm/client-types")
    public Result<Void> updateClientTypes(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        AdminRoleGuard.requireSystemConfigAdmin(user);
        String apiKey = (String) body.get("apiKey");
        String clientTypes = (String) body.get("clientTypes");
        if (apiKey == null || apiKey.trim().isEmpty() || clientTypes == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接口键与可用端必填");
        }
        apiPermissionService.updateClientTypes(apiKey, clientTypes,
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 手动触发接口清单同步（新增 Controller 后调用）。
     *
     * @param user 当前用户
     * @return 同步数量
     */
    @PostMapping("/api/admin/api-perm/sync")
    public Result<Integer> sync(@CurrentUser LoanUser user) {
        AdminRoleGuard.requireSystemConfigAdmin(user);
        int n = syncService.syncApis();
        apiPermissionService.refreshRules(user == null ? "system" : user.getName());
        return Result.ok(n);
    }

    /**
     * 内部接口：返回鉴权规则（网关 Redis 缺失时兜底拉取）。
     *
     * @return 规则 JSON
     */
    @GetMapping("/internal/api-perm/rules")
    public Result<Map<String, Object>> internalRules(
            @RequestParam(value = "token", required = false) String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内部接口令牌无效");
        }
        return Result.ok(apiPermissionService.internalRules());
    }

}
