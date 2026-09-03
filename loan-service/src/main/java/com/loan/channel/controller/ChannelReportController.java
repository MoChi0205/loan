package com.loan.channel.controller;

import com.loan.api.dto.PageResult;
import com.loan.channel.dto.ChannelBatchQueryRequest;
import com.loan.channel.service.ChannelDataScopeService;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 合作渠道 Web 工作区：本人录入客户的分析报告只读查询。 */
@RestController
@RequestMapping("/api/channel/report")
@RequiredArgsConstructor
public class ChannelReportController {

    private final ChannelDataScopeService channelDataScopeService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> reportPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderDir,
            @CurrentUser LoanUser user) {
        return Result.ok(channelDataScopeService.reportPage(status, grade, keyword,
                PageParams.page(page), PageParams.size(size), orderDir, user));
    }

    /** 按报告业务编号批量查询本人客户报告摘要，保持请求顺序并忽略未命中项。 */
    @PostMapping("/batch")
    public Result<List<Map<String, Object>>> reportBatch(@RequestBody ChannelBatchQueryRequest body,
                                                          @CurrentUser LoanUser user) {
        return Result.ok(channelDataScopeService.reportBatch(body == null ? null : body.getCodes(), user));
    }

    @GetMapping("/{reportNo}")
    public Result<Map<String, Object>> reportDetail(@PathVariable String reportNo,
                                                     @CurrentUser LoanUser user) {
        return Result.ok(channelDataScopeService.reportDetail(reportNo, user));
    }
}
