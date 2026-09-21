package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.serviceops.dto.ActivityTimelineDTO;
import com.loan.serviceops.dto.FollowRecordCreateRequest;
import com.loan.serviceops.service.ClientActivityService;
import com.loan.serviceops.service.FollowRecordService;
import com.loan.serviceops.service.ServiceOperationScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web 员工端客户级跟进与统一活动回放。 */
@RestController
@RequestMapping("/api/admin/client")
@RequiredArgsConstructor
public class AdminClientActivityController {

    private final FollowRecordService followRecordService;
    private final ClientActivityService activityService;
    private final ServiceOperationScopeService scopeService;

    @PostMapping("/{clientCode}/follow-record")
    @OpLog(bizType = "客户跟进", action = "CREATE")
    public Result<String> createFollow(@PathVariable String clientCode,
                                       @RequestBody FollowRecordCreateRequest request,
                                       @CurrentUser LoanUser user) {
        return Result.ok(followRecordService.create(clientCode, request, user));
    }

    @GetMapping("/{clientCode}/activity-timeline")
    public Result<PageResult<ActivityTimelineDTO>> activityTimeline(
            @PathVariable String clientCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        return Result.ok(activityService.timeline(clientCode, false,
                PageParams.page(page), PageParams.size(size)));
    }
}
