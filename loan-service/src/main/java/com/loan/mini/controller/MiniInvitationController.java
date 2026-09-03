package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.invitation.service.InvitationService;
import com.loan.mini.service.MiniInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：邀请绑定 / 我的邀请码 / 邀请记录（推荐有礼）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/invitation")
@RequiredArgsConstructor
public class MiniInvitationController {

    private final InvitationService invitationService;
    private final MiniInvitationService miniInvitationService;

    /**
     * 绑定邀请码（登录后可补绑）。
     *
     * @param body {inviteCode}
     * @param user 当前客户
     * @return 绑定结果（referrerType / referrerName）
     */
    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(miniInvitationService.bind(body.get("inviteCode"), clientCode, user.getUserId()));
    }

    /**
     * 我的邀请码（幂等生成，7 天有效）。
     *
     * @param user 当前客户
     * @return 邀请码
     */
    @GetMapping("/mine")
    public Result<String> mine(@CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(invitationService.myInviteCode(clientCode, user.getUserId()));
    }

    /**
     * 我的邀请记录（通过我的邀请码注册的客户）。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前客户
     * @return 记录分页
     */
    @GetMapping("/records")
    public Result<PageResult<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(invitationService.myRecords(clientCode, PageParams.page(page), PageParams.size(size)));
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
