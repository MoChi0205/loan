package com.loan.mini.controller;

import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 小程序端：渠道产品管理（C9 撤销审批 + 申请删除 + 撤销删除）。
 *
 * <p><b>渠道侧</b>（{@code /api/mini/product/*}）：管理自有产品，
 * 录入后提交审批，可撤销审批；已上架产品可申请删除、撤销删除申请。
 *
 * <p><b>运营 / 超管侧</b>（{@code /api/mini/partner-product/delete/*}）：
 * 对待删除申请做终审，批准即从全量库移除，驳回则回到已上架。
 * 两端合并在本 Controller 便于统一维护 C9 状态机。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniProductController {

    private final MiniProductService miniProductService;

    /* ==================== 渠道侧 ==================== */

    /**
     * 我的产品列表（渠道视角，沙箱内仅本渠道录入）。
     *
     * @param user 当前渠道用户
     * @return 产品列表（含状态 / 驳回原因 / 产品名称与额度利率）
     */
    @GetMapping("/product/list")
    public Result<List<Map<String, Object>>> myProducts(@CurrentUser LoanUser user) {
        requireChannel(user);
        return Result.ok(miniProductService.myProducts(user.getUserId()));
    }

    /**
     * 保存产品（新建草稿，或编辑草稿 / 已驳回后重提）。
     *
     * @param body 产品信息（bankProductCode 必填）
     * @param user 当前渠道用户
     * @return { code, action: CREATED | UPDATED }
     */
    @PostMapping("/product")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body,
                                            @CurrentUser LoanUser user) {
        requireChannel(user);
        return Result.ok(miniProductService.save(body, user));
    }

    /**
     * 产品详情（编辑态回填，C9）。
     *
     * <p>按审批单号定位本渠道的申请，返回表单可回填字段：
     * bankProductCode / cooperateUntil / amountMin / amountMax / requirement。
     *
     * @param code 审批单号
     * @param user 当前渠道用户
     * @return 产品详情（表单回填用）
     */
    @GetMapping("/product/{code}")
    public Result<Map<String, Object>> detail(@PathVariable String code, @CurrentUser LoanUser user) {
        requireChannel(user);
        return Result.ok(miniProductService.detail(code, user));
    }

    /**
     * 编辑产品（草稿 / 已驳回可编辑重提）。
     *
     * <p><b>C9 语义：</b>按路径 code 定位审批单更新（编辑态编码不可变），
     * 而非按 bankProductCode 重建草稿。
     *
     * @param code 审批单号
     * @param body 产品信息
     * @param user 当前渠道用户
     * @return 保存结果
     */
    @PutMapping("/product/{code}")
    public Result<Map<String, Object>> update(@PathVariable String code,
                                              @RequestBody Map<String, Object> body,
                                              @CurrentUser LoanUser user) {
        requireChannel(user);
        return Result.ok(miniProductService.update(code, body, user));
    }

    /**
     * 提交审批（草稿 / 已驳回 → 待审批）。
     *
     * @param code 审批单号
     * @param user 当前渠道用户
     * @return 空
     */
    @PostMapping("/product/{code}/submit")
    public Result<Void> submit(@PathVariable String code, @CurrentUser LoanUser user) {
        requireChannel(user);
        miniProductService.submit(code, user);
        return Result.ok(null);
    }

    /**
     * 撤销审批（待审批 → 草稿），无需审批，即时生效。
     *
     * @param code 审批单号
     * @param user 当前渠道用户
     * @return 空
     */
    @PostMapping("/product/{code}/revoke")
    public Result<Void> revoke(@PathVariable String code, @CurrentUser LoanUser user) {
        requireChannel(user);
        miniProductService.revoke(code, user);
        return Result.ok(null);
    }

    /**
     * 申请删除（已上架 → 待删除），需我司运营 / 超管终审。
     *
     * @param code 审批单号
     * @param body 删除原因（reason）
     * @param user 当前渠道用户
     * @return 空
     */
    @PostMapping("/product/{code}/delete-apply")
    public Result<Void> applyDelete(@PathVariable String code,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @CurrentUser LoanUser user) {
        requireChannel(user);
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        miniProductService.applyDelete(code, reason, user);
        return Result.ok(null);
    }

    /**
     * 撤销删除申请（待删除 → 已上架）。
     *
     * @param code 审批单号
     * @param user 当前渠道用户
     * @return 空
     */
    @PostMapping("/product/{code}/delete-cancel")
    public Result<Void> cancelDelete(@PathVariable String code, @CurrentUser LoanUser user) {
        requireChannel(user);
        miniProductService.cancelDelete(code, user);
        return Result.ok(null);
    }

    /* ==================== 运营 / 超管侧：删除终审（C9 闭环） ==================== */

    /**
     * 待删除审批列表（运营 / 超管视角）。
     *
     * @param user 当前员工
     * @return 待删除产品列表
     */
    @GetMapping("/partner-product/delete/pending")
    public Result<List<Map<String, Object>>> pendingDelete(@CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(miniProductService.pendingDeleteList());
    }

    /**
     * 删除审批终审（批准 → 从全量库移除；驳回 → 回到已上架）。
     *
     * @param approvalNo 审批单号
     * @param body       { approve: boolean, opinion: string }
     * @param user       当前员工
     * @return 空
     */
    @PostMapping("/partner-product/delete/{approvalNo}/audit")
    public Result<Void> auditDelete(@PathVariable String approvalNo,
                                    @RequestBody Map<String, Object> body,
                                    @CurrentUser LoanUser user) {
        requireStaff(user);
        boolean approve = body != null && Boolean.parseBoolean(String.valueOf(body.get("approve")));
        String opinion = body == null || body.get("opinion") == null ? "" : String.valueOf(body.get("opinion"));
        miniProductService.auditDelete(approvalNo, approve, opinion, user.getUserNo());
        return Result.ok(null);
    }

    /* ==================== 权限校验 ==================== */

    /** 渠道：可管理自有产品 */
    private void requireChannel(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (!LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅渠道合作方可管理产品");
        }
    }

    /** 员工：可做删除终审 */
    private void requireStaff(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅运营 / 超级管理员可做删除终审");
        }
    }
}
