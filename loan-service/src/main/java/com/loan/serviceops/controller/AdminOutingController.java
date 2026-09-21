package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.service.OutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Web 员工端外出名单及本人双打卡接口。 */
@RestController
@RequestMapping("/api/admin/outing")
@RequiredArgsConstructor
public class AdminOutingController {

    private final OutingService outingService;

    @GetMapping("/day")
    public Result<PageResult<StaffOutingDTO>> pageDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(outingService.day(date, status, user,
                PageParams.page(page), PageParams.size(size)));
    }

    @PostMapping
    @OpLog(bizType = "员工外出", action = "CREATE")
    public Result<String> create(@RequestBody OutingCreateRequest request,
                                 @CurrentUser LoanUser user) {
        return Result.ok(outingService.create(request, user));
    }

    @PostMapping("/{outingNo}/depart")
    @OpLog(bizType = "员工外出", action = "DEPART_CHECK_IN")
    public Result<Void> depart(@PathVariable String outingNo,
                               @RequestBody LocationCheckInRequest request,
                               @CurrentUser LoanUser user) {
        outingService.depart(outingNo, request, user);
        return Result.ok();
    }

    @PostMapping("/{outingNo}/return")
    @OpLog(bizType = "员工外出", action = "RETURN_CHECK_IN")
    public Result<Void> returnFromOuting(@PathVariable String outingNo,
                                         @RequestBody LocationCheckInRequest request,
                                         @CurrentUser LoanUser user) {
        outingService.returnFromOuting(outingNo, request, user);
        return Result.ok();
    }
}
