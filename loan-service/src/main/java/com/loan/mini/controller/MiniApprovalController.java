package com.loan.mini.controller;

import com.loan.approval.service.ApprovalService;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
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
 * 小程序端：审批中心统一入口（C4c）。
 *
 * <p>聚合无归宿客户分配（ALLOCATION）、产品审核（PRODUCT）、附件下载（DOWNLOAD）三类审批，
 * 对外提供「待审计数 → 待审列表 → 审批动作」三段式统一接口，替代原先分散在
 * {@link MiniClientController} 的 {@code /allocation-approvals/*} 接口。</p>
 *
 * <p><b>类型白名单：</b>由配置 {@code loan.mini.approval.types} 控制（当前为 {@code ALLOCATION}），
 * 白名单外类型不会出现在待审列表 / 计数中，审批动作亦被拒绝。</p>
 *
 * <p><b>权限：</b>统一走 {@link MiniRoleGuard}；ALLOCATION 需运营 / 超管 / 老板，
 * PRODUCT / DOWNLOAD 额外放通部门经理。</p>
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/approval")
@RequiredArgsConstructor
public class MiniApprovalController {

    private final ApprovalService approvalService;
    private final MiniRoleGuard miniRoleGuard;

    /**
     * 各类型待审计数（审批中心角标）。
     *
     * @param user 当前用户（需审批权限）
     * @return { PRODUCT, DOWNLOAD, ALLOCATION, TOTAL }
     */
    @GetMapping("/counts")
    public Result<Map<String, Object>> counts(@CurrentUser LoanUser user) {
        miniRoleGuard.requireApprover(user);
        return Result.ok(approvalService.pendingCounts());
    }

    /**
     * 统一待审列表。
     *
     * <p>{@code type=ALL} 时按 {@code ALLOCATION} 校验权限——当前白名单仅开放 ALLOCATION，
     * 合并结果不会含其他类型数据，故以最严口径校验即可。</p>
     *
     * @param type 审批类型（ALL / PRODUCT / DOWNLOAD / ALLOCATION）
     * @param page 页码
     * @param size 每页大小
     * @param user 当前用户
     * @return { page, size, total, records（每条含 type）, paginationHint }
     */
    @GetMapping("/pending")
    public Result<Map<String, Object>> pending(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        miniRoleGuard.requireApproverFor(ApprovalService.TYPE_ALL.equals(type)
                ? ApprovalService.TYPE_ALLOCATION : type, user);
        return Result.ok(approvalService.unifiedPending(type, page, size));
    }

    /**
     * 统一审批动作（通过 / 驳回）。
     *
     * @param type       审批类型（PRODUCT / DOWNLOAD / ALLOCATION）
     * @param approvalNo 审批单号
     * @param body       { approve: Boolean（默认 true）, opinion: String（驳回必填） }
     * @param user       当前用户
     * @return 成功标记
     */
    @PostMapping("/{type}/{approvalNo}/audit")
    public Result<Void> audit(@PathVariable String type,
                             @PathVariable String approvalNo,
                             @RequestBody(required = false) Map<String, Object> body,
                             @CurrentUser LoanUser user) {
        miniRoleGuard.requireApproverFor(type, user);
        // 小程序端受配置白名单约束：未开放类型直接拒绝（管理端统一接口不受此限制）
        approvalService.requireTypeEnabled(type);
        boolean approve = body == null || body.get("approve") == null
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        String opinion = body == null || body.get("opinion") == null
                ? null : String.valueOf(body.get("opinion"));
        approvalService.unifiedAudit(type, approvalNo, approve, opinion, user);
        return Result.ok();
    }
}
