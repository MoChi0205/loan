package com.loan.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.api.dto.PageResult;
import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计服务：按 traceUuid 查询匹配全链路时间线（含命中产品与规则明细）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final MatchTraceMapper matchTraceMapper;
    private final MatchRuleLogMapper matchRuleLogMapper;

    /**
     * 按 traceUuid 查询审计时间线。
     *
     * @param traceUuid 链路 UUID
     * @return { trace, rules }，不存在返回 null
     */
    public Map<String, Object> queryByTraceUuid(String traceUuid) {
        if (!StringUtils.hasText(traceUuid)) {
            return null;
        }
        MatchTrace trace = matchTraceMapper.selectOne(
                new LambdaQueryWrapper<MatchTrace>().eq(MatchTrace::getTraceUuid, traceUuid));
        if (trace == null) {
            return null;
        }
        List<MatchRuleLog> rules = matchRuleLogMapper.selectList(
                new LambdaQueryWrapper<MatchRuleLog>()
                        .eq(MatchRuleLog::getTraceId, trace.getId())
                        .orderByAsc(MatchRuleLog::getId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trace", trace);
        result.put("rules", rules);
        return result;
    }

    /**
     * 分页查询审计记录（按时间倒序，支持多维过滤）。
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
    public PageResult<MatchTrace> page(String traceUuid, String customerGroup, String totalResult,
                                       Integer mismatchFlag, String startTime, String endTime,
                                       int page, int size) {
        LambdaQueryWrapper<MatchTrace> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(traceUuid)) {
            wrapper.like(MatchTrace::getTraceUuid, traceUuid.trim());
        }
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(MatchTrace::getCustomerGroup, customerGroup.trim());
        }
        if (StringUtils.hasText(totalResult)) {
            wrapper.eq(MatchTrace::getTotalResult, totalResult.trim());
        }
        if (mismatchFlag != null) {
            wrapper.eq(MatchTrace::getMismatchFlag, mismatchFlag);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(MatchTrace::getExecutedAt, startTime.trim());
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(MatchTrace::getExecutedAt, endTime.trim());
        }
        wrapper.orderByDesc(MatchTrace::getExecutedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MatchTrace> result =
                matchTraceMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }
}
