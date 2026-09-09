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
import com.loan.common.util.PageParams;
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
import java.util.List;

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
        return Result.ok(clientAllocationService.pageUnassigned(keyword, PageParams.page(page),
                PageParams.size(size)));
    }

    /**
     * 顾问申请认领：未归属客户直接认领，已归属本人幂等通过，
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
        if (!java.util.Arrays.asList("DEPT_MANAGER", "BOSS", "SUPER_ADMIN", "SUPER").contains(role)) {
            throw new com.loan.exception.BusinessException(
                    com.loan.common.ResultCode.FORBIDDEN, "仅部门经理、老板或超级管理员可直接指定客户归属人");
        }
        // 兼容旧参数名 adviserStaffCode，新参数为 targetStaffCode
        String target = body == null ? null : body.get("targetStaffCode");
        if (!org.springframework.util.StringUtils.hasText(target) && body != null) {
            target = body.get("adviserStaffCode");
        }
        return Result.ok(clientAllocationService.directAssign(clientCode, target, user));
    }

    @PostMapping("/batch-assign")
    @OpLog(bizType = "客户归属", action = "BATCH_MANAGER_ASSIGN")
    public Result<Map<String, Object>> batchAssign(@RequestBody Map<String, Object> body,
                                                    @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        @SuppressWarnings("unchecked") List<String> codes = body == null ? null : (List<String>) body.get("clientCodes");
        String target = body == null ? null : String.valueOf(body.get("targetStaffCode"));
        return Result.ok(clientAllocationService.batchAssign(codes, target, user));
    }

    @PostMapping("/batch-recycle")
    @OpLog(bizType = "客户归属", action = "BATCH_CLIENT_RECYCLE")
    public Result<Map<String, Object>> batchRecycle(@RequestBody Map<String, Object> body,
                                                     @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        @SuppressWarnings("unchecked") List<String> codes = body == null ? null : (List<String>) body.get("clientCodes");
        return Result.ok(clientAllocationService.batchRecycle(codes, user));
    }

    @PostMapping("/batch-claim")
    @OpLog(bizType = "客户归属", action = "BATCH_PUBLIC_SEA_CLAIM")
    public Result<Map<String, Object>> batchClaim(@RequestBody Map<String, Object> body,
                                                   @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        @SuppressWarnings("unchecked") List<String> codes = body == null ? null : (List<String>) body.get("clientCodes");
        return Result.ok(clientAllocationService.batchClaim(codes, user));
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
     * 顾问主动释放自己的客户回公海（无需审批）。
     *
     * <p>仅客户当前归属本人可操作，清空归属并置冷却，不删档案。</p>
     *
     * @param clientCode 客户编码
     * @param user 当前登录用户
     * @return { clientCode, released=true, fromOwnerStaffCode }
     */
    @PostMapping("/{clientCode}/release")
    @OpLog(bizType = "客户归属", action = "CLIENT_SELF_RELEASE")
    public Result<Map<String, Object>> release(@PathVariable String clientCode,
                                               @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        return Result.ok(clientAllocationService.selfRelease(clientCode, user));
    }

    /**
     * 顾问记录客户跟进：刷新最后跟进时间，避免超期自动回收进公海。
     *
     * @param clientCode 客户编码
     * @param body { content: 跟进内容（可选） }
     * @param user 当前登录用户
     * @return { clientCode, followedAt }
     */
    @PostMapping("/{clientCode}/follow")
    @OpLog(bizType = "客户跟进", action = "FOLLOW_UP")
    public Result<Map<String, Object>> follow(@PathVariable String clientCode,
                                              @RequestBody(required = false) Map<String, String> body,
                                              @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        String content = body == null ? null : body.get("content");
        return Result.ok(clientAllocationService.followUp(clientCode, user, content));
    }

    /**
     * 客户分配/跟进历史（t_lead_allocation_record 复用流水）。
     *
     * <p>返回按时间倒序的流转记录，含 actionType（CLAIM_APPLY / CLAIM_APPROVED /
     * CLIENT_RECYCLE / FOLLOW_UP / CLIENT_RECYCLE_MANUAL / MANAGER_ASSIGN 等）、
     * from/to 工号、remark 与操作人。</p>
     *
     * @param clientCode 客户编码
     * @param page       页码
     * @param size       每页大小
     * @return 客户流转历史
     */
    @GetMapping("/{clientCode}/history")
    public Result<Map<String, Object>> history(@PathVariable String clientCode,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return Result.ok(clientAllocationService.history(clientCode, PageParams.page(page), PageParams.size(size)));
    }

    /**
     * 客户轻量分页（多维筛选：关键字 / 姓名 / 手机号 / 企业名 / 信用代码 / 建档时间）。
     *
     * @param keyword        关键字（可选：姓名 / 企业名 / 手机号哈希精确）
     * @param name           联系人姓名模糊（可选）
     * @param phone          手机号精确（可选，SHA-256 哈希匹配）
     * @param enterpriseName 企业名称模糊（可选）
     * @param creditCode     统一社会信用代码精确（可选，SHA-256 哈希匹配）
     * @param ownerStaffCode 归属人工号（可选；顾问/渠道查本人客户时传入）
     * @param createdAtStart 建档起始时间（可选）
     * @param createdAtEnd   建档截止时间（可选）
     * @param dealTimeStart  成交起始时间（可选，联表 t_service_order 已成交工单）
     * @param dealTimeEnd    成交截止时间（可选，联表 t_service_order 已成交工单）
     * @param page           页码
     * @param size           每页大小
     * @return 客户轻量列表
     */
    @GetMapping("/page-lite")
    public Result<PageResult<Map<String, Object>>> pageLite(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String enterpriseName,
            @RequestParam(required = false) String creditCode,
            @RequestParam(required = false) String ownerStaffCode,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime createdAtStart,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime createdAtEnd,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dealTimeStart,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dealTimeEnd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @RequestParam(defaultValue = "ALL") String scope,
            @CurrentUser LoanUser user) {
        miniRoleGuard.requireStaff(user);
        String scopedOwner = ownerStaffCode;
        String ownerDeptCode = null;
        String seaLevel = null;
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase();
        if ("MY".equals(normalizedScope)) {
            scopedOwner = user.getUserNo();
        } else if ("TEAM".equals(normalizedScope)) {
            if (!"DEPT_MANAGER".equalsIgnoreCase(user.getRoleCode())) {
                throw new com.loan.exception.BusinessException(
                        com.loan.common.ResultCode.FORBIDDEN, "仅部门经理可查看团队客户");
            }
            ownerDeptCode = user.getDeptCode();
        } else if ("COMPANY_SEA".equals(normalizedScope)) {
            seaLevel = "COMPANY";
        } else if ("TEAM_SEA".equals(normalizedScope)) {
            if (!"DEPT_MANAGER".equalsIgnoreCase(user.getRoleCode())) {
                throw new com.loan.exception.BusinessException(
                        com.loan.common.ResultCode.FORBIDDEN, "仅部门经理可查看团队公海");
            }
            seaLevel = "TEAM";
            ownerDeptCode = user.getDeptCode();
        }
        return Result.ok(clientService.pageLite(keyword, name, phone, enterpriseName, creditCode,
                scopedOwner, createdAtStart, createdAtEnd, dealTimeStart, dealTimeEnd,
                PageParams.page(page), PageParams.size(size), orderBy, orderDir, ownerDeptCode, seaLevel));
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
