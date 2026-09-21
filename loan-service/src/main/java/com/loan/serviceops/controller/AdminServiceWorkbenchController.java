package com.loan.serviceops.controller;

import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.serviceops.dto.DailyServiceWorkbenchDTO;
import com.loan.serviceops.service.DailyServiceWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Web 今日服务台聚合接口。 */
@RestController
@RequestMapping("/api/admin/workbench")
@RequiredArgsConstructor
public class AdminServiceWorkbenchController {

    private final DailyServiceWorkbenchService workbenchService;

    @GetMapping("/daily-lists")
    public Result<DailyServiceWorkbenchDTO> dailyLists(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(workbenchService.daily(date, user,
                PageParams.page(page), PageParams.size(size)));
    }
}
