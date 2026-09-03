package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：推荐有礼（我的奖励汇总与记录）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/reward")
@RequiredArgsConstructor
public class MiniRewardController {

    private final MiniRewardService miniRewardService;

    /**
     * 我的奖励汇总。
     *
     * @param user 当前客户
     * @return 汇总
     */
    @GetMapping("/mine/summary")
    public Result<Map<String, Object>> summary(@CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(miniRewardService.mySummary(clientCode));
    }

    /**
     * 我的奖励记录。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前客户
     * @return 奖励分页
     */
    @GetMapping("/mine")
    public Result<PageResult<Map<String, Object>>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(miniRewardService.myRewards(clientCode, PageParams.page(page), PageParams.size(size)));
    }

    /**
     * 取当前客户编码。
     *
     * @param user 当前用户
     * @return 客户编码
     */
    private String requireClient(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user.getUserNo();
    }
}
