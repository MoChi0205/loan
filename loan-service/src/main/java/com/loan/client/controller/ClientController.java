package com.loan.client.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.client.model.ClientUpdateRequest;
import com.loan.client.service.ClientService;
import com.loan.client.service.ClientAllocationService;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.mini.service.MiniRoleGuard;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户档案 HTTP 接口（管理端：轻量查询切片 + P0-6 档案详情 / 编辑）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/client")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ClientAllocationService clientAllocationService;
    private final MiniRoleGuard miniRoleGuard;

    /**
     * 未分配客户池。新微信客户只要 ownerStaffCode 为空即自动进入，无需伪造线索。
     */
    @GetMapping("/unassigned/page")
    public Result<PageResult<Map<String, Object>>> unassignedPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        return Result.ok(clientAllocationService.pageUnassigned(keyword, page <= 0 ? 1 : page,
                size <= 0 ? 10 : Math.min(size, 100)));
    }

    /**
     * 顾问申请认领：未归属客户创建分配审批，已归属本人幂等通过，
     * 已归属他人则创建转移审批，任何场景都不允许顾问直接覆盖归属。
     */
    @PostMapping("/{clientCode}/claim")
    @OpLog(bizType = "客户归属", action = "CLAIM_APPLY")
    public Result<Map<String, Object>> claim(@PathVariable String clientCode,
                                              @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (!"ADVISER".equals(role)) {
            throw new com.loan.exception.BusinessException(
                    com.loan.common.ResultCode.FORBIDDEN, "仅顾问可从未分配客户池申请认领");
        }
        return Result.ok(clientAllocationService.applyTransfer(clientCode, user.getUserNo(), user));
    }

    /**
     * 管理者/老板直接指定客户归属人（D39 业务变更）：<b>立即落归属、不生成审批单</b>。
     *
     * <p>可操作角色：团队管理者（DEPT_MANAGER）/ 老板（BOSS）/ 运营 / 超级管理员。
     * 目标归属人可为顾问或团队管理者。前端需在此操作前弹窗二次确认。</p>
     *
     * @param clientCode 客户编码
     * @param body { targetStaffCode | adviserStaffCode } 目标归属人工号
     * @param user 当前登录用户
     * @return { clientCode, ownerStaffName, status=APPROVED, direct=true, needApproval=false }
     */
    @PostMapping("/{clientCode}/assign")
    @OpLog(bizType = "客户归属", action = "MANAGER_ASSIGN")
    public Result<Map<String, Object>> assign(@PathVariable String clientCode,
                                               @RequestBody Map<String, String> body,
                                               @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (!java.util.Arrays.asList("DEPT_MANAGER", "BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER").contains(role)) {
            throw new com.loan.exception.BusinessException(
                    com.loan.common.ResultCode.FORBIDDEN, "仅管理者或老板可直接指定客户归属人");
        }
        // 兼容旧参数名 adviserStaffCode，新参数为 targetStaffCode
        String target = body == null ? null : body.get("targetStaffCode");
        if (!org.springframework.util.StringUtils.hasText(target) && body != null) {
            target = body.get("adviserStaffCode");
        }
        return Result.ok(clientAllocationService.directAssign(clientCode, target, user));
    }

    /**
     * 管理端手动回收客户进公海（管理者操作，覆盖冷却期）。
     *
     * <p>可操作角色：团队管理者 / 老板 / 运营 / 超级管理员（与分配审批管理员一致）。
     * 仅清空归属并置冷却，不删除客户档案；如需重新归属走分配流程。</p>
     *
     * @param clientCode 客户编码
     * @param user 当前登录用户
     * @return { clientCode, recycled=true, fromOwnerStaffCode }
     */
    @PostMapping("/{clientCode}/recycle")
    @OpLog(bizType = "客户归属", action = "CLIENT_RECYCLE_MANUAL")
    public Result<Map<String, Object>> recycle(@PathVariable String clientCode,
                                               @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(clientAllocationService.manualRecycle(clientCode, user));
    }

    /**
     * 客户轻量分页（关键字：编码 / 联系人 / 企业 / 手机号）。
     *
     * @param keyword 关键字（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 客户轻量列表
     */
    @GetMapping("/page-lite")
    public Result<PageResult<Map<String, Object>>> pageLite(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(clientService.pageLite(keyword, page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 档案合并视图（P0-6）：基础信息 + 企业 + 个人档案 + 认证状态 + 邀请链 + VIP + 审计字段。
     *
     * @param clientCode 客户编码
     * @return 档案合并视图
     */
    @GetMapping("/{clientCode}")
    public Result<Map<String, Object>> detail(@PathVariable String clientCode) {
        return Result.ok(clientService.getClientDetail(clientCode));
    }

    /**
     * 档案编辑（P0-6）：基础信息 + 个人档案字段合并更新，敏感字段加密落库、读取脱敏。
     *
     * @param clientCode 客户编码
     * @param req        编辑请求
     * @return 更新后的档案合并视图
     */
    @PutMapping("/{clientCode}")
    public Result<Map<String, Object>> update(@PathVariable String clientCode,
                                              @RequestBody ClientUpdateRequest req) {
        return Result.ok(clientService.updateClientDetail(clientCode, req));
    }
}
