package com.loan.reward.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.reward.service.RewardService;
import com.loan.reward.entity.RewardRule;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 推荐奖励 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/reward")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    /**
     * 奖励分页。
     *
     * @param status  状态（可选：PENDING_AUDIT/GRANTED/REJECTED/VOID）
     * @param keyword 奖励单号/工单号/客户编码（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 奖励分页
     */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(rewardService.page(status, keyword, startDate, endDate,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 奖励规则列表。
     *
     * @return 全部规则（含停用）
     */
    @GetMapping("/rule")
    public Result<List<RewardRule>> listRules() {
        return Result.ok(rewardService.listRules());
    }

    /**
     * 保存奖励规则（新增 / 更新）。
     *
     * @param rule 规则体（产品编码 + 客群 + 直推/间推比例 + 上下限）
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/rule")
    @OpLog(bizType = "奖励规则", action = "SAVE")
    public Result<Void> saveRule(@RequestBody RewardRule rule, @CurrentUser LoanUser user) {
        rewardService.saveRule(rule, user == null ? "system" : user.getUserNo());
        return Result.ok();
    }

    /**
     * 停用奖励规则。
     *
     * @param id   规则 ID
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/rule/{id}/disable")
    @OpLog(bizType = "奖励规则", action = "DISABLE")
    public Result<Void> disableRule(@PathVariable Long id, @CurrentUser LoanUser user) {
        rewardService.disableRule(id, user == null ? "system" : user.getUserNo());
        return Result.ok();
    }

    /**
     * 审核奖励单（发放 / 驳回）。
     *
     * @param rewardNo 奖励单号
     * @param body     { approve, opinion, rewardAmount, manualAdjustReason }
     * @param user     当前用户
     * @return 成功标记
     */
    @PostMapping("/{rewardNo}/audit")
    @OpLog(bizType = "奖励", action = "AUDIT")
    public Result<Void> audit(@PathVariable String rewardNo, @RequestBody Map<String, Object> body,
                              @CurrentUser LoanUser user) {
        Object amt = body.get("rewardAmount");
        BigDecimal rewardAmount = amt == null ? null : new BigDecimal(String.valueOf(amt));
        rewardService.audit(rewardNo,
                Boolean.TRUE.equals(body.get("approve")),
                (String) body.get("opinion"),
                user == null ? "system" : user.getUserNo(),
                rewardAmount,
                (String) body.get("manualAdjustReason"));
        return Result.ok();
    }

    /**
     * 作废奖励单。
     *
     * @param rewardNo 奖励单号
     * @param body     { reason }
     * @param user     当前用户
     * @return 成功标记
     */
    @PostMapping("/{rewardNo}/void")
    @OpLog(bizType = "奖励", action = "VOID")
    public Result<Void> voidReward(@PathVariable String rewardNo, @RequestBody Map<String, Object> body,
                                   @CurrentUser LoanUser user) {
        rewardService.voidReward(rewardNo, (String) body.get("reason"),
                user == null ? "system" : user.getUserNo());
        return Result.ok();
    }
}
