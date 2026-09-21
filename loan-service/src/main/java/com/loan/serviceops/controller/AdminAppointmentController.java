package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.serviceops.dto.AppointmentChangeRequest;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.dto.ReasonRequest;
import com.loan.serviceops.dto.StaffAppointmentDTO;
import com.loan.serviceops.service.AppointmentService;
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

/** Web 员工端预约及履约接口。 */
@RestController
@RequestMapping("/api/admin/appointment")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/day")
    public Result<PageResult<StaffAppointmentDTO>> pageDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String serviceMethod,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.day(date, serviceMethod, status, user,
                PageParams.page(page), PageParams.size(size)));
    }

    @PostMapping
    @OpLog(bizType = "客户预约", action = "CREATE")
    public Result<String> create(@RequestBody AppointmentCreateRequest request,
                                 @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.createForStaff(request, user));
    }

    @PostMapping("/{appointmentNo}/confirm")
    @OpLog(bizType = "客户预约", action = "CONFIRM")
    public Result<Void> confirm(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.confirmByStaff(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/arrive")
    @OpLog(bizType = "客户预约", action = "ARRIVE")
    public Result<Void> arrive(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.arrive(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/start")
    @OpLog(bizType = "客户预约", action = "START")
    public Result<Void> start(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.startService(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/complete")
    @OpLog(bizType = "客户预约", action = "COMPLETE")
    public Result<Void> complete(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.complete(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/no-show")
    @OpLog(bizType = "客户预约", action = "NO_SHOW")
    public Result<Void> markNoShow(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.markNoShow(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/exception-cancel")
    @OpLog(bizType = "客户预约", action = "EXCEPTION_CANCEL")
    public Result<Void> cancelException(@PathVariable String appointmentNo,
                                        @RequestBody ReasonRequest request,
                                        @CurrentUser LoanUser user) {
        appointmentService.cancel(appointmentNo, request == null ? null : request.getReason(), user, true);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/cancel")
    @OpLog(bizType = "客户预约", action = "CANCEL")
    public Result<Void> cancel(@PathVariable String appointmentNo,
                               @RequestBody(required = false) ReasonRequest request,
                               @CurrentUser LoanUser user) {
        appointmentService.cancel(appointmentNo, request == null ? null : request.getReason(), user, false);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/reschedule")
    @OpLog(bizType = "客户预约", action = "RESCHEDULE")
    public Result<String> reschedule(@PathVariable String appointmentNo,
                                     @RequestBody AppointmentChangeRequest request,
                                     @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.reschedule(appointmentNo, request, user, false));
    }

    @PostMapping("/{appointmentNo}/exception-reschedule")
    @OpLog(bizType = "客户预约", action = "EXCEPTION_RESCHEDULE")
    public Result<String> rescheduleException(@PathVariable String appointmentNo,
                                              @RequestBody AppointmentChangeRequest request,
                                              @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.reschedule(appointmentNo, request, user, true));
    }
}
