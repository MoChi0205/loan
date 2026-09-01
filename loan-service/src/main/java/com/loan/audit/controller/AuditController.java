package com.loan.audit.controller;

import com.loan.api.dto.PageResult;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.service.AuditService;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 按 traceUuid 查询匹配全链路时间线。
     *
     * @param traceUuid 链路 UUID
     * @return 时间线（trace + rules）
     */
    @GetMapping("/{traceUuid}")
    public Result<Map<String, Object>> detail(@PathVariable String traceUuid) {
        Map<String, Object> result = auditService.queryByTraceUuid(traceUuid);
        if (result == null) {
            return Result.fail(ResultCode.DATA_NOT_FOUND);
        }
        return Result.ok(result);
    }

    /**
     * 分页查询审计记录（支持 traceUuid / 客群 / 结果 / 异常 / 时间范围过滤）。
     *
     * @param traceUuid     链路 UUID（模糊）
     * @param customerGroup 客群（ENTERPRISE / PERSONAL）
     * @param totalResult   匹配结果（PASS / CONDITION / REJECT / ...）
     * @param mismatchFlag  异常标记（1=存在异常分支）
     * @param startTime     开始时间（yyyy-MM-dd HH:mm:ss）
     * @param endTime       结束时间
     * @param page          页码
     * @param size          每页大小
     * @return 审计记录分页
     */
    @GetMapping("/page")
    public Result<PageResult<MatchTrace>> page(
            @RequestParam(required = false) String traceUuid,
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String totalResult,
            @RequestParam(required = false) Integer mismatchFlag,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(auditService.page(traceUuid, customerGroup, totalResult, mismatchFlag,
                startTime, endTime, page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100)));
    }
}
