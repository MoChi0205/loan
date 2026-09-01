package com.loan.screening.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.screening.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 初筛执行 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/screening")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    /**
     * 执行初筛并生成报告。
     *
     * @param body { clientCode, facts: {...} }
     * @param user 当前用户
     * @return 报告编号
     */
    @PostMapping("/run")
    @OpLog(bizType = "初筛", action = "RUN")
    public Result<String> run(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) body.get("facts");
        String applyCity = body.get("applyCity") == null ? null : String.valueOf(body.get("applyCity"));
        return Result.ok(screeningService.run((String) body.get("clientCode"), facts,
                user == null ? "system" : user.getName(), applyCity));
    }
}
