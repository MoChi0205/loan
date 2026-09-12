package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniClientService;
import com.loan.mini.service.MiniRoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：客户查重与归属流转（C10 自动查重 + C2 归属流转）。
 *
 * <p><b>权限：</b>仅企业员工（STAFF）可替客户操作；客户无查重/分配权限；
 * 渠道合作方按沙箱隔离，不可查看或分配客户。
 *
 * <p><b>流程（C10）：</b>输入关键词 → 自动查重 →
 * 命中走 {@code claim}（本人幂等 / 他人或无归属走审批），
 * 未命中走 {@code create}（先进入未分配客户池，再按角色分配或认领）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/client")
@RequiredArgsConstructor
public class MiniClientController {

    private final MiniClientService miniClientService;
    private final MiniRoleGuard miniRoleGuard;

    /**
     * 客户查重（C10）：企业名模糊 / 手机号精确 / 统一社会信用代码精确，任一命中即返回。
     *
     * @param keyword 关键词（≥2 字）
     * @param user    当前用户
     * @return 命中客户（含 hasOwner），未命中返回 null
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword, @CurrentUser LoanUser user) {
        requireStaff(user);
        if (keyword == null || keyword.trim().length() < 2) {
            return Result.ok(null);
        }
        return Result.ok(miniClientService.search(keyword));
    }

    /**
     * 录入新客户进入未分配客户池（D39）。
     *
     * @param body 客户信息（entName 必填）
     * @param user 当前员工
     * @return { clientCode, ownerStaffCode, action: CREATED_UNASSIGNED | EXISTED }
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.create(body, user));
    }

    /**
     * 申请分配已有客户（C2 情形 B）。
     *
     * @param clientCode 客户编码
     * @param body       申请理由（reason）
     * @param user       当前员工
     * @return { result: AUTO_CLAIMED | PENDING_APPROVAL, approvalNo? }
     */
    @PostMapping("/{clientCode}/claim")
    public Result<Map<String, Object>> claim(@PathVariable String clientCode,
                                             @RequestBody(required = false) Map<String, Object> body,
                                             @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.claim(clientCode, user));
    }

    /**
     * 查询分配申请审批状态（无归宿分支轮询用）。
     *
     * @param clientCode 客户编码
     * @param user       当前员工
     * @return { status: PENDING | APPROVED | REJECTED, rejectReason? }
     */
    @GetMapping("/{clientCode}/claim-status")
    public Result<Map<String, Object>> claimStatus(@PathVariable String clientCode, @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.claimStatus(clientCode, user));
    }

    /** 顾问释放本人客户回公海（无需审批）。 */
    @PostMapping("/{clientCode}/release")
    public Result<Map<String, Object>> release(@PathVariable String clientCode, @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.release(clientCode, user));
    }

    /* ==================== 我的客户 / 客户公海（员工侧列表，D74） ==================== */

    /**
     * 「我的客户」列表：仅本人归属客户。
     *
     * <p>所有员工角色口径一致（用户 2026-09-10 确认「统一只显示本人归属」），
     * 不做团队 / 全司放大；渠道与客户由 {@link #requireStaff} 拒绝。
     *
     * @param keyword 企业名模糊关键词，可为空
     * @param page    页码
     * @param size    每页大小
     * @param user    当前登录员工
     * @return 本人归属客户分页摘要
     */
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> myClients(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.myClients(keyword, PageParams.page(page), PageParams.size(size), user));
    }

    /**
     * 客户公海列表（15-客户公海团队客户与分配回收规则 §11/§12）。
     *
     * <p>公司公海全员可见；团队公海仅本部门可见（无部门账号返回空，fail-closed）。
     * 认领仍走 {@link #claim}，不在此处落归属。
     *
     * @param keyword  企业名模糊关键词，可为空
     * @param seaLevel ENTERPRISE 公司公海 / TEAM 团队公海
     * @param page     页码
     * @param size     每页大小
     * @param user     当前登录员工
     * @return 公海客户分页摘要
     */
    @GetMapping("/sea")
    public Result<PageResult<Map<String, Object>>> seaClients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String seaLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniClientService.seaClients(keyword, seaLevel,
                PageParams.page(page), PageParams.size(size), user));
    }

    /**
     * 「团队客户」列表：本部门成员（排除本人）名下客户，供部门经理回收。
     *
     * <p>仅 {@code DEPT_MANAGER} 可访问（15-规则 §10：部门经理「团队客户」与「我的客户」独立展示）；
     * 其他员工角色的团队 / 全司视图仍只在 Web 管理端。
     *
     * @param keyword 企业名模糊关键词，可为空
     * @param page    页码
     * @param size    每页大小
     * @param user    当前登录用户
     * @return 本部门成员名下客户分页摘要
     */
    @GetMapping("/team")
    public Result<PageResult<Map<String, Object>>> teamClients(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        miniRoleGuard.requireDeptManager(user);
        return Result.ok(miniClientService.teamClients(keyword,
                PageParams.page(page), PageParams.size(size), user));
    }

    /**
     * 回收客户进公海（15-规则 §32/§33/§34）。
     *
     * <p>部门经理回收本团队客户 → 团队公海；老板 / 运营 / 超级管理员 → 公司公海。
     * 跨团队回收由服务层拒绝；覆盖冷却期，不删除客户档案。
     *
     * @param clientCode 客户编码
     * @param user       当前登录用户
     * @return { clientCode, recycled=true, fromOwnerStaffCode }
     */
    @PostMapping("/{clientCode}/recycle")
    public Result<Map<String, Object>> recycleClient(@PathVariable String clientCode,
                                                     @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(miniClientService.recycle(clientCode, user));
    }

    /* ==================== B3：无归宿分配审批（运营/超管） ==================== */

    /**
     * 分配待审列表（运营/超管审批中心入口）。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前用户（运营/超管）
     * @deprecated 已迁至 {@code GET /api/mini/approval/pending?type=ALLOCATION}，
     *             见 {@link MiniApprovalController#pending}。本接口仅为兼容存量小程序版本保留，
     *             新端不得再调用，后续版本将下线。
     */
    @Deprecated
    @GetMapping("/allocation-approvals/pending")
    public Result<com.loan.api.dto.PageResult<Map<String, Object>>> pendingAllocations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(miniClientService.pendingAllocations(page, size));
    }

    /**
     * 通过分配审批：客户归属流转给申请人。
     *
     * @param approvalNo 审批单号
     * @param user       当前用户（运营/超管）
     * @deprecated 已迁至 {@code POST /api/mini/approval/ALLOCATION/{approvalNo}/audit}
     *             （body {@code {"approve":true}}），见 {@link MiniApprovalController#audit}。
     */
    @Deprecated
    @PostMapping("/allocation-approvals/{approvalNo}/approve")
    public Result<Map<String, Object>> approveAllocation(@PathVariable String approvalNo,
                                                         @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(miniClientService.approveAllocation(approvalNo, user));
    }

    /**
     * 驳回分配审批。
     *
     * @param approvalNo 审批单号
     * @param body       { opinion: 驳回意见（必填） }
     * @param user       当前用户（运营/超管）
     * @deprecated 已迁至 {@code POST /api/mini/approval/ALLOCATION/{approvalNo}/audit}
     *             （body {@code {"approve":false,"opinion":"..."}}），
     *             见 {@link MiniApprovalController#audit}。
     */
    @Deprecated
    @PostMapping("/allocation-approvals/{approvalNo}/reject")
    public Result<Map<String, Object>> rejectAllocation(@PathVariable String approvalNo,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        String opinion = body == null ? null : body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        return Result.ok(miniClientService.rejectAllocation(approvalNo, opinion, user));
    }

    /**
     * 权限校验：仅企业员工可替客户操作，渠道与客户无此权限。
     *
     * @param user 当前用户
     */
    private void requireStaff(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可查看或分配客户");
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅企业员工可替客户操作");
        }
    }
}
