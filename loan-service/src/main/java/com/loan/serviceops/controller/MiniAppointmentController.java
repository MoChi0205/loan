package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.serviceops.dto.AppointmentChangeRequest;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.dto.CustomerAppointmentDTO;
import com.loan.serviceops.dto.ReasonRequest;
import com.loan.serviceops.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 小程序/H5 客户预约接口；客户编号始终取登录态。 */
@RestController
@RequestMapping("/api/mini/appointment")
@RequiredArgsConstructor
public class MiniAppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/mine")
    public Result<PageResult<CustomerAppointmentDTO>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.mine(user, PageParams.page(page), PageParams.size(size)));
    }

    @PostMapping("/request")
    public Result<String> request(@RequestBody AppointmentCreateRequest request,
                                  @RequestHeader(value = "X-Client-Type", required = false) String clientType,
                                  @CurrentUser LoanUser user) {
        String terminal = "H5".equalsIgnoreCase(clientType) || "WEB".equalsIgnoreCase(clientType)
                ? "H5" : "MINI_APP";
        return Result.ok(appointmentService.createForCustomer(request, user, terminal));
    }

    @PostMapping("/{appointmentNo}/confirm")
    public Result<Void> confirm(@PathVariable String appointmentNo, @CurrentUser LoanUser user) {
        appointmentService.confirmByCustomer(appointmentNo, user);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/cancel")
    public Result<Void> cancel(@PathVariable String appointmentNo,
                               @RequestBody(required = false) ReasonRequest request,
                               @CurrentUser LoanUser user) {
        appointmentService.cancel(appointmentNo, request == null ? null : request.getReason(), user, false);
        return Result.ok();
    }

    @PostMapping("/{appointmentNo}/reschedule")
    public Result<String> reschedule(@PathVariable String appointmentNo,
                                     @RequestBody AppointmentChangeRequest request,
                                     @CurrentUser LoanUser user) {
        return Result.ok(appointmentService.reschedule(appointmentNo, request, user, false));
    }
}
