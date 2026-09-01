package com.loan.approval.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.approval.service.ApprovalService;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
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
 * 审批 HTTP 接口（Web 管理端）：产品审核工单 + 附件下载审批。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final MiniClientService miniClientService;
    private final MiniRoleGuard miniRoleGuard;

    // ============================================================
    // 产品审核
    // ============================================================

    /**
     * 产品审核工单分页。
     */
    @GetMapping("/product/page")
    public Result<PageResult<Map<String, Object>>> productPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(approvalService.productPage(status, keyword,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 产品审核详情。
     */
    @GetMapping("/product/{approvalNo}")
    public Result<Map<String, Object>> productDetail(@PathVariable String approvalNo) {
        return Result.ok(approvalService.productDetail(approvalNo));
    }

    /**
     * 产品审核（通过 / 驳回）。
     *
     * @param approvalNo 审核工单号
     * @param body       { approve, opinion }
     * @param user       当前用户
     * @return 成功标记
     */
    @PostMapping("/product/{approvalNo}/audit")
    @OpLog(bizType = "产品审核", action = "AUDIT")
    public Result<Void> productAudit(@PathVariable String approvalNo, @RequestBody Map<String, Object> body,
                                     @CurrentUser LoanUser user) {
        approvalService.productAudit(approvalNo,
                Boolean.TRUE.equals(body.get("approve")),
                (String) body.get("opinion"),
                user == null ? "system" : user.getUserNo());
        return Result.ok();
    }

    // ============================================================
    // 附件下载审批
    // ============================================================

    /**
     * 提交无水印下载申请。
     *
     * @param body { attachmentIds, purpose, expectDays }
     * @param user 当前用户
     * @return 申请单号
     */
    @PostMapping("/download/apply")
    @OpLog(bizType = "下载审批", action = "APPLY")
    public Result<String> downloadApply(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        return Result.ok(approvalService.applyDownload(
                (String) body.get("attachmentIds"),
                (String) body.get("purpose"),
                body.get("expectDays") == null ? null : Integer.valueOf(body.get("expectDays").toString()),
                user == null ? null : user.getUserNo(),
                user == null ? "system" : user.getName()));
    }

    /**
     * 下载审批分页。
     */
    @GetMapping("/download/page")
    public Result<PageResult<Map<String, Object>>> downloadPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(approvalService.downloadPage(status, keyword,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 下载审批（通过生成 24h 限时链接 / 驳回）。
     */
    @PostMapping("/download/{approvalNo}/audit")
    @OpLog(bizType = "下载审批", action = "AUDIT")
    public Result<Void> downloadAudit(@PathVariable String approvalNo, @RequestBody Map<String, Object> body,
                                      @CurrentUser LoanUser user) {
        approvalService.downloadAudit(approvalNo,
                Boolean.TRUE.equals(body.get("approve")),
                (String) body.get("opinion"),
                user == null ? "system" : user.getUserNo());
        return Result.ok();
    }

    /**
     * 作废下载审批单。
     */
    @PostMapping("/download/{approvalNo}/void")
    @OpLog(bizType = "下载审批", action = "VOID")
    public Result<Void> downloadVoid(@PathVariable String approvalNo, @CurrentUser LoanUser user) {
        approvalService.voidDownload(approvalNo, user == null ? "system" : user.getUserNo());
        return Result.ok();
    }

    // ============================================================
    // 无归宿客户分配审批（管理端，与小程序端共用 MiniClientService 实现）
    // ============================================================

    /**
     * 分配待审分页。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分配待审分页（含企业名 / 联系人 / 手机掩码 / 申请人姓名）
     */
    @GetMapping("/allocation/pending")
    public Result<PageResult<Map<String, Object>>> allocationPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        // D0-4：分配审批仅 OPERATOR / SUPER_ADMIN / SUPER / BOSS 可见可审（不含 DEPT_MANAGER）
        miniRoleGuard.requireApprover(user);
        return Result.ok(miniClientService.pendingAllocations(
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100)));
    }

    /**
     * 通过分配审批：客户归属流转给申请人。
     *
     * @param approvalNo 审批单号
     * @param user       当前用户
     * @return { approvalNo, status, clientCode }
     */
    @PostMapping("/allocation/{approvalNo}/approve")
    @OpLog(bizType = "分配审批", action = "APPROVE")
    public Result<Map<String, Object>> allocationApprove(@PathVariable String approvalNo,
                                                         @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(miniClientService.approveAllocation(approvalNo, user));
    }

    /**
     * 驳回分配审批。
     *
     * @param approvalNo 审批单号
     * @param body       { opinion: 驳回意见（必填） }
     * @param user       当前用户
     * @return { approvalNo, status }
     */
    @PostMapping("/allocation/{approvalNo}/reject")
    @OpLog(bizType = "分配审批", action = "REJECT")
    public Result<Map<String, Object>> allocationReject(@PathVariable String approvalNo,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        @CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        String opinion = body == null || body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        return Result.ok(miniClientService.rejectAllocation(approvalNo, opinion, user));
    }

    // ============================================================
    // 统一审批中心（跨类型：PRODUCT / DOWNLOAD / ALLOCATION）
    // ============================================================

    /**
     * 统一待审列表。
     *
     * <p>返回体为 Map（page/size/total/records/paginationHint），
     * {@code paginationHint=SEGMENTED} 表示各类型各取一页后合并，非全局连续分页。</p>
     *
     * @param type 审批类型（ALL / PRODUCT / DOWNLOAD / ALLOCATION）
     * @param page 页码
     * @param size 每页大小
     * @return 合并后的待审列表
     */
    @GetMapping("/unified/pending")
    public Result<Map<String, Object>> unifiedPending(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(approvalService.unifiedPending(type, page, size));
    }

    /**
     * 统一待审计数。
     *
     * @return { PRODUCT, DOWNLOAD, ALLOCATION, TOTAL }
     */
    @GetMapping("/unified/counts")
    public Result<Map<String, Object>> unifiedCounts() {
        return Result.ok(approvalService.pendingCounts());
    }

    /**
     * 统一审批动作（按类型路由到各自既有审批实现）。
     *
     * @param type       审批类型（PRODUCT / DOWNLOAD / ALLOCATION）
     * @param approvalNo 审批单号
     * @param body       { approve, opinion }
     * @param user       当前用户
     * @return 成功标记
     */
    @PostMapping("/unified/{type}/{approvalNo}/audit")
    @OpLog(bizType = "统一审批", action = "AUDIT")
    public Result<Void> unifiedAudit(@PathVariable String type, @PathVariable String approvalNo,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @CurrentUser LoanUser user) {
        // D0-4：ALLOCATION 类型按审批分配管理员校验（不含 DEPT_MANAGER）；其余类型含部门经理
        miniRoleGuard.requireApproverFor(type, user);
        boolean approve = body == null || body.get("approve") == null
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        String opinion = body == null || body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        approvalService.unifiedAudit(type, approvalNo, approve, opinion, user);
        return Result.ok();
    }
}
