package com.loan.blacklist.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.blacklist.service.BlacklistService;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<Void> add(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        blacklistService.add((String) body.get("dimension"),
                (String) body.get("value"),
                (String) body.get("reasonType"),
                (String) body.get("reasonRemark"),
                user == null ? "system" : user.getName());
        return Result.ok();
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
    public Result<Void> release(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        blacklistService.release(Long.valueOf(body.get("id").toString()),
                user == null ? "system" : user.getUserNo());
        return Result.ok();
    }
}
