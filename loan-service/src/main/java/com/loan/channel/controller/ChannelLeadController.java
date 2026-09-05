package com.loan.channel.controller;

import com.loan.api.dto.PageResult;
import com.loan.channel.service.ChannelDataScopeService;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.lead.entity.Lead;
import com.loan.lead.service.LeadService;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 合作渠道 Web 工作区：本人线索录入与查询。 */
@RestController
@RequestMapping("/api/channel/lead")
@RequiredArgsConstructor
public class ChannelLeadController {

    private final LeadService leadService;
    private final ChannelDataScopeService channelDataScopeService;

    @PostMapping
    @OpLog(bizType = "渠道线索", action = "CREATE_APPLY")
    public Result<String> createLead(@RequestBody Lead lead, @CurrentUser LoanUser user) {
        String channelNo = channelDataScopeService.requireChannel(user);
        lead.setSource("CHANNEL");
        lead.setFollowStatus("PENDING_APPROVAL");
        return Result.ok(leadService.create(lead, channelNo, user.getName()));
    }

    @GetMapping("/page")
    public Result<PageResult<Lead>> leadPage(
            @RequestParam(required = false) String leadType,
            @RequestParam(required = false) String followStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @CurrentUser LoanUser user) {
        String channelNo = channelDataScopeService.requireChannel(user);
        return Result.ok(leadService.pageByRecorder(channelNo, leadType, followStatus, keyword,
                PageParams.page(page), PageParams.size(size), orderBy, orderDir));
    }
}
