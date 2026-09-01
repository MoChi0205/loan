package com.loan.sensitive.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.sensitive.dto.SensitiveApplyViewResp;
import com.loan.sensitive.dto.SensitiveQuotaVO;
import com.loan.sensitive.service.SensitiveViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 敏感数据查看授权接口（线索手机号受控查看）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/lead/sensitive")
@RequiredArgsConstructor
public class SensitiveViewController {

    private final SensitiveViewService sensitiveViewService;

    /**
     * 申请查看线索敏感手机号（受限角色走授权 + 日限额 + 留痕）。
     *
     * @param body { leadNo }
     * @param user 当前用户
     * @return 申请查看响应（授权后揭示明文）
     */
    @PostMapping("/apply-view")
    @OpLog(bizType = "敏感数据", action = "APPLY_VIEW")
    public Result<SensitiveApplyViewResp> applyView(@RequestBody Map<String, String> body,
                                                    @CurrentUser LoanUser user) {
        String leadNo = body == null ? null : body.get("leadNo");
        return Result.ok(sensitiveViewService.applyView(leadNo,
                user == null ? null : user.getUserNo(),
                user == null ? null : user.getRoleCode()));
    }

    /**
     * 查询当前用户当日查看额度（上限 / 已用 / 剩余）。
     *
     * @param user 当前用户
     * @return 额度 VO
     */
    @GetMapping("/quota")
    public Result<SensitiveQuotaVO> quota(@CurrentUser LoanUser user) {
        return Result.ok(sensitiveViewService.getQuota(user == null ? null : user.getUserNo()));
    }
}
