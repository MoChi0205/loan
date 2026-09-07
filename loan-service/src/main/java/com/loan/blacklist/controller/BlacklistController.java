package com.loan.blacklist.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.blacklist.service.BlacklistService;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 风控黑名单 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistService blacklistService;

    /**
     * 黑名单分页。
     */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(blacklistService.page(dimension, status, keyword,
                PageParams.page(page), PageParams.size(size), orderBy, orderDir));
    }

    /**
     * 新增黑名单。
     *
     * @param body { dimension, value, reasonType, reasonRemark? }
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping
    @OpLog(bizType = "黑名单", action = "CREATE")
    public Result<Void> add(@RequestBody(required = false) Map<String, Object> body, @CurrentUser LoanUser user) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "黑名单参数不能为空");
        }
        blacklistService.add(text(body.get("dimension")),
                text(body.get("value")),
                text(body.get("reasonType")),
                text(body.get("reasonRemark")),
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解禁（仅老板）。
     *
     * @param body { id }
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/release")
    @OpLog(bizType = "黑名单", action = "RELEASE")
    public Result<Void> release(@RequestBody(required = false) Map<String, Object> body, @CurrentUser LoanUser user) {
        Object rawId = body == null ? null : body.get("id");
        if (rawId == null || !StringUtils.hasText(String.valueOf(rawId))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "黑名单记录标识必填");
        }
        final long id;
        try {
            id = Long.parseLong(String.valueOf(rawId));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "黑名单记录标识格式错误");
        }
        if (id <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "黑名单记录标识格式错误");
        }
        blacklistService.release(id,
                user == null ? "system" : user.getUserNo());
        return Result.ok();
    }
}
