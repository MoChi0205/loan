package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import com.loan.common.ResultCode;
import com.loan.engine.enums.Grade;
import com.loan.exception.BusinessException;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告查询服务（P0-4）：小程序侧按 reportNo + 归属校验读取报告详情。
 *
 * <p>对客返回内容强制脱敏（✅评审决策 08-28）：仅档位 / 三档总结果 / 产品数量 / 用户评级 /
 * 逐条规则说明，不含任何产品名 / 银行名 / 额度 / 利率明细；产品明细仅 Web 管理端可见
 * （见 {@code ReportService.screeningDetail}）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ClientScreeningMapper screeningMapper;
    private final MatchTraceMapper matchTraceMapper;
    private final MatchRuleLogMapper matchRuleLogMapper;

    /**
     * 小程序侧报告详情（按 reportNo 读取，返回脱敏内容）。
     *
     * <p><b>归属校验语义（C3 角色二分）：</b>
     * <ul>
     *   <li>客户 CUSTOMER：必传 clientCode，且必须等于报告归属（clientProfileCode），否则 FORBIDDEN；</li>
     *   <li>企业员工 STAFF：传 null / 空串表示「全量可看」，跳过归属校验（同 allReports）；</li>
     *   <li>渠道 CHANNEL：由 Controller 层沙箱隔离直接拒绝，本方法不感知。</li>
     * </ul>
     *
     * @param reportNo   报告编号（业务唯一ID）
     * @param clientCode 客户编码（归属校验；null / 空串跳过校验，供员工全量查看复用）
     * @return 报告详情（不含产品明细）
     */
    public Map<String, Object> miniDetail(String reportNo, String clientCode) {
        if (!StringUtils.hasText(reportNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "报告编号必填");
        }
        ClientScreening s = screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getReportNo, reportNo));
        if (s == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "报告不存在");
        }
        // 归属校验：仅客户视角（clientCode 非空）执行；员工全量视角（null/空）跳过
        if (StringUtils.hasText(clientCode) && !clientCode.equals(s.getClientProfileCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该报告");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportNo", s.getReportNo());
        m.put("grade", s.getGrade());
        m.put("totalResult", resolveTotalResult(s));
        m.put("productCount", s.getProductCount());
        m.put("rating", rating(s.getGrade()));
        m.put("bankCount", s.getBankCount());
        m.put("passCount", s.getPassCount());
        m.put("conditionCount", s.getConditionCount());
        m.put("rejectCount", s.getRejectCount());
        m.put("adviceJson", s.getAdviceJson());
        m.put("vipFlag", s.getVipFlag());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        m.put("ruleLogs", ruleLogs(s.getMatchTraceUuid()));
        return m;
    }

    /**
     * 三档总结果：优先取审计主表 totalResult，缺省按命中数推导。
     *
     * @param s 初筛报告
     * @return PASS / CONDITION / REJECT / SKIP_SEGMENT_MISMATCH
     */
    private String resolveTotalResult(ClientScreening s) {
        if (StringUtils.hasText(s.getMatchTraceUuid())) {
            MatchTrace trace = matchTraceMapper.selectOne(new LambdaQueryWrapper<MatchTrace>()
                    .eq(MatchTrace::getTraceUuid, s.getMatchTraceUuid()).last("limit 1"));
            if (trace != null && StringUtils.hasText(trace.getTotalResult())) {
                return trace.getTotalResult();
            }
        }
        if (s.getPassCount() != null && s.getPassCount() > 0) {
            return "PASS";
        }
        if (s.getConditionCount() != null && s.getConditionCount() > 0) {
            return "CONDITION";
        }
        if (s.getRejectCount() != null && s.getRejectCount() > 0) {
            return "REJECT";
        }
        return "SKIP_SEGMENT_MISMATCH";
    }

    /**
     * 用户评级：档位编码 → 中文（高/中/低）。
     *
     * @param gradeCode 档位编码
     * @return 评级文案
     */
    private String rating(String gradeCode) {
        Grade grade = Grade.fromCode(gradeCode);
        return grade == null ? null : grade.getName();
    }

    /**
     * 逐条规则日志（脱敏，仅规则编码 + 表达式 + 结果，不含产品明细）。
     *
     * @param traceUuid 审计链路 UUID
     * @return 规则日志列表
     */
    private List<Map<String, Object>> ruleLogs(String traceUuid) {
        List<Map<String, Object>> logs = new ArrayList<>();
        if (!StringUtils.hasText(traceUuid)) {
            return logs;
        }
        MatchTrace trace = matchTraceMapper.selectOne(new LambdaQueryWrapper<MatchTrace>()
                .eq(MatchTrace::getTraceUuid, traceUuid).last("limit 1"));
        if (trace == null) {
            return logs;
        }
        List<MatchRuleLog> rows = matchRuleLogMapper.selectList(new LambdaQueryWrapper<MatchRuleLog>()
                .eq(MatchRuleLog::getTraceId, trace.getId())
                .orderByAsc(MatchRuleLog::getId));
        for (MatchRuleLog row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", row.getRuleCode());
            item.put("expression", row.getExpression());
            item.put("result", row.getStepResult());
            logs.add(item);
        }
        return logs;
    }
}
