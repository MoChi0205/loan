package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.serviceops.dto.ActivityTimelineDTO;
import com.loan.serviceops.service.ClientActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客户可见服务时间线；只返回 CUSTOMER 事件。 */
@RestController
@RequestMapping("/api/mini/service")
@RequiredArgsConstructor
public class MiniServiceTimelineController {

    private final ClientActivityService activityService;

    @GetMapping("/timeline")
    public Result<PageResult<ActivityTimelineDTO>> timeline(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        if (user == null || !LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅客户可以查看本人服务时间线");
        }
        return Result.ok(activityService.timeline(user.getUserNo(), true,
                PageParams.page(page), PageParams.size(size)));
    }
}
