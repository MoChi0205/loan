package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import com.loan.common.ResultCode;
import com.loan.engine.enums.Grade;
import com.loan.exception.BusinessException;
import com.loan.report.dto.CustomerReportDetail;
import com.loan.report.dto.ReportRuleLog;
import com.loan.report.dto.StaffReportDetail;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告查询服务（P0-4）：小程序侧按 reportNo + 归属校验读取报告详情。
 *
 * <p>客户视角仅返回报告元数据，产品、银行、准入规则与匹配统计全部留在员工视角。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ClientScreeningMapper screeningMapper;
    private final MatchTraceMapper matchTraceMapper;
    private final MatchRuleLogMapper matchRuleLogMapper;

    /** 客户报告详情：客户编码必填并执行报告归属校验。 */
    public CustomerReportDetail customerDetail(String reportNo, String clientCode) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "客户身份无效");
        }
        ClientScreening s = requireReport(reportNo);
        if (!clientCode.equals(s.getClientProfileCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该报告");
        }
        CustomerReportDetail detail = new CustomerReportDetail();
        detail.setReportNo(s.getReportNo());
        detail.setStatus(s.getStatus());
        detail.setCreatedAt(s.getCreatedAt());
        detail.setDataSourceNotice("本报告仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口。");
        return detail;
    }

    /** 公司员工内部报告详情；调用方必须先完成 STAFF 身份和数据范围校验。 */
    public StaffReportDetail staffDetail(String reportNo) {
        ClientScreening s = requireReport(reportNo);
        StaffReportDetail detail = new StaffReportDetail();
        detail.setReportNo(s.getReportNo());
        detail.setClientProfileCode(s.getClientProfileCode());
        detail.setTemplateCode(s.getTemplateCode());
        detail.setStatus(s.getStatus());
        detail.setCreatedAt(s.getCreatedAt());
        detail.setGrade(s.getGrade());
        detail.setTotalResult(resolveTotalResult(s));
        detail.setProductCount(s.getProductCount());
        detail.setRating(rating(s.getGrade()));
        detail.setBankCount(s.getBankCount());
        detail.setPassCount(s.getPassCount());
        detail.setConditionCount(s.getConditionCount());
        detail.setRejectCount(s.getRejectCount());
        detail.setAdviceJson(s.getAdviceJson());
        detail.setVipFlag(s.getVipFlag());
        detail.setRuleLogs(ruleLogs(s.getMatchTraceUuid()));
        return detail;
    }

    private ClientScreening requireReport(String reportNo) {
        if (!StringUtils.hasText(reportNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "报告编号必填");
        }
        ClientScreening screening = screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getReportNo, reportNo));
        if (screening == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "报告不存在");
        }
        return screening;
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
    private List<ReportRuleLog> ruleLogs(String traceUuid) {
        List<ReportRuleLog> logs = new ArrayList<>();
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
            ReportRuleLog item = new ReportRuleLog();
            item.setRuleCode(row.getRuleCode());
            item.setExpression(row.getExpression());
            item.setResult(row.getStepResult());
            logs.add(item);
        }
        return logs;
    }
}
