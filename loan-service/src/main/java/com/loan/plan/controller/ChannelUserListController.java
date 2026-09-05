package com.loan.plan.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.plan.entity.ChannelUserList;
import com.loan.plan.model.ChannelUserListBatchRequest;
import com.loan.plan.model.ChannelUserListQuery;
import com.loan.plan.model.ChannelUserListUpdateRequest;
import com.loan.plan.service.ChannelUserListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 渠道本地白/黑名单 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/channel-user-list")
@RequiredArgsConstructor
public class ChannelUserListController {

    private final ChannelUserListService listService;

    /** 分页查询名单 */
    @GetMapping("/page")
    public Result<PageResult<ChannelUserList>> page(ChannelUserListQuery query) {
        return Result.ok(listService.page(query));
    }

    /** 按业务编码查询单条名单 */
    @GetMapping("/{listCode}")
    public Result<ChannelUserList> detail(@PathVariable String listCode) {
        return Result.ok(listService.detail(listCode));
    }

    /** 按业务编码批量查询名单 */
    @PostMapping("/batch-query")
    public Result<List<ChannelUserList>> batchQuery(@RequestBody ChannelUserListBatchRequest request) {
        return Result.ok(listService.batchQuery(request == null ? null : request.getListCodes()));
    }

    /** 批量新增名单 */
    @PostMapping
    @SuppressWarnings("unchecked")
    public Result<Integer> add(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        String channelCode = (String) body.get("channelCode");
        String customerGroup = (String) body.get("customerGroup");
        String listType = (String) body.get("listType");
        List<String> keys = (List<String>) body.get("keys");
        return Result.ok(listService.add(channelCode, customerGroup, listType, keys, operatorName(user)));
    }

    /** 按业务编码修改名单，业务编码自身不可修改 */
    @PutMapping("/{listCode}")
    public Result<String> update(@PathVariable String listCode,
                                 @RequestBody ChannelUserListUpdateRequest request) {
        listService.update(listCode, request);
        return Result.ok("ok");
    }

    /** 删除单条 */
    @DeleteMapping("/{listCode}")
    public Result<String> delete(@PathVariable String listCode) {
        listService.delete(listCode);
        return Result.ok("ok");
    }

    /** 批量删除 */
    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody ChannelUserListBatchRequest request) {
        return Result.ok(listService.batchDelete(request == null ? null : request.getListCodes()));
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
