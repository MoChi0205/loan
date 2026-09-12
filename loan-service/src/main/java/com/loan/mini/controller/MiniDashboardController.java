package com.loan.mini.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序端：首页统计行（我的客户 / 团队成员 / 全司客户 等）。
 *
 * <p><b>权限：</b>接口由网关按「角色 × 接口 × 端」统一鉴权；服务侧再按
 * {@code userType/roleCode/deptCode} 收敛数据范围——渠道仅本人、部门经理仅本部门、
 * 老板/超管为全司、顾问/运营为本人（见 {@link DashboardService#homeStats}）。
 *
 * <p><b>口径：</b>返回值只包含当前角色允许的指标键，不暴露其它范围数据；
 * 前端按角色取用对应键，标签与展示形态由 {@code loan-mini/utils/roles.js} 定义。
 * 全程不返回任何业务单号（D68 / 02-红线 #7）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/dashboard")
@RequiredArgsConstructor
public class MiniDashboardController {

    private final DashboardService dashboardService;

    /**
     * 首页统计行指标。
     *
     * @param user 当前登录用户（含 userType / roleCode / userNo / deptCode）
     * @return 该角色允许的指标键值（键见 {@link DashboardService#homeStats}）
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@CurrentUser LoanUser user) {
        if (user == null) {
            return Result.ok(new LinkedHashMap<>());
        }
        return Result.ok(dashboardService.homeStats(
                user.getUserType(), user.getRoleCode(), user.getUserNo(), user.getDeptCode()));
    }
}
