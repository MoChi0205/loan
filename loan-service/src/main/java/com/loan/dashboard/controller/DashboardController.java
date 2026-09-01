package com.loan.dashboard.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台 HTTP 接口（待办统计）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 待办统计。
     */
    @GetMapping("/todo")
    public Result<Map<String, Object>> todo(@CurrentUser LoanUser user) {
        return Result.ok(dashboardService.todo(
                user == null ? null : user.getRoleCode(),
                user == null ? null : user.getUserNo()));
    }
}
