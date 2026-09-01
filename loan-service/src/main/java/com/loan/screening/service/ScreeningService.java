package com.loan.screening.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.engine.aggregate.GradeAggregator;
import com.loan.engine.dto.MatchResultVO;
import com.loan.engine.dto.ModuleMatchVO;
import com.loan.engine.dto.ProductMatchVO;
import com.loan.engine.dto.StepMatchVO;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.enums.Grade;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.execute.ProductPlan;
import com.loan.engine.service.MatchService;
import com.loan.exception.BusinessException;
import com.loan.plan.service.PlanLoaderService;
import com.loan.report.entity.ClientScreening;
import com.loan.report.entity.ReportTemplate;
import com.loan.report.entity.ScreeningProduct;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.mapper.ReportTemplateMapper;
import com.loan.report.mapper.ScreeningProductMapper;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 初筛执行服务：选客户 + 经营事实 → 规则引擎匹配 → 落审计 + 生成初筛报告。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ClientProfileMapper clientProfileMapper;
    private final PlanLoaderService planLoaderService;
    private final MatchService matchService;
    private final MatchTraceMapper matchTraceMapper;
    private final MatchRuleLogMapper matchRuleLogMapper;
    private final ClientScreeningMapper screeningMapper;
    private final ReportTemplateMapper reportTemplateMapper;
    private final SubmissionService submissionService;
    private final ScreeningProductMapper screeningProductMapper;

    /**
     * 执行初筛并生成报告（无客户端幂等键的入口，兼容既有调用方）。
     *
     * @param clientCode 客户编码
     * @param facts      经营事实（企业：annualTaxAmount/annualInvoiceAmount/foundYears/industry 等）
     * @param operator   操作人
     * @return 报告编号
     */
    public String run(String clientCode, Map<String, Object> facts, String operator, String applyCity) {
        return run(clientCode, facts, operator, applyCity, null);
    }

    /**
     * 执行初筛并生成报告（P0-4 完整链路）。
     *
     * <p>执行前先幂等落 {@code t_client_submission}（client_submit_id 防重）+ 拆 facts 落
     * {@code t_client_business_fact}；执行后回填匹配审计（trace 关联客户档案/提交单）与
     * 提交单状态（match_trace_no 回填、status=MATCHED）。
     *
     * @param clientCode     客户编码
     * @param facts          经营事实（企业：annualTaxAmount/annualInvoiceAmount/foundYears/industry 等）
     * @param operator       操作人
     * @param applyCity      申请城市（可选）
     * @param clientSubmitId 客户端幂等键（可选，同键不重复落库）
     * @return 报告编号
     */
    @Transactional(rollbackFor = Exception.class)
    public String run(String clientCode, Map<String, Object> facts, String operator, String applyCity,
                      String clientSubmitId) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户必选");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        // P0-4：执行前幂等落提交单 + 经营事实（key 即 t_rule.field_code，与引擎入参对齐）
        ClientSubmission submission = submissionService.submit(client.getClientCode(),
                client.getCustomerGroup(), facts, clientSubmitId, operator);
        CustomerGroup group = CustomerGroup.fromCode(client.getCustomerGroup());
        if (group == null) {
            group = CustomerGroup.ENTERPRISE;
        }
        AdmissionContext context = AdmissionContext.builder()
                .traceUuid(UUID.randomUUID().toString().replace("-", ""))
                .customerGroup(group)
                .fieldValues(facts == null ? new java.util.HashMap<>() : facts)
                .build();

        long start = System.currentTimeMillis();
        // TODO(渠道V2): 渠道应从客户档案的邀请/渠道绑定关系推导（client → invitation → channel），
        // 目前暂传 null 加载全渠道策略；待渠道绑定链路打通后改为传具体 channelCode
        List<ProductPlan> productPlans = planLoaderService.loadProductPlans(null, group, applyCity);
        MatchResultVO vo = matchService.match(context, productPlans);
        long duration = System.currentTimeMillis() - start;

        // 落匹配审计（trace + rule logs），回填实际的客户档案/提交单内部 ID
        Long traceId = saveTrace(context.getTraceUuid(), group.getCode(), vo, duration,
                client.getId(), submission == null ? null : submission.getId());
        saveRuleLogs(traceId, vo);

        // 生成初筛报告
        ClientScreening screening = new ClientScreening();
        screening.setReportNo(BizIdGenerator.generate("report"));
        screening.setClientProfileCode(client.getClientCode());
        screening.setMatchTraceUuid(context.getTraceUuid());
        ReportTemplate template = reportTemplateMapper.selectOne(new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getStatus, "ACTIVE").orderByDesc(ReportTemplate::getCreatedAt).last("limit 1"));
        screening.setTemplateCode(template == null ? null : template.getTemplateCode());
        Grade grade = resolveGrade(vo);
        screening.setGrade(grade == null ? null : grade.getCode());
        screening.setBankCount(vo.getBankCount());
        screening.setProductCount(vo.getProductCount());
        screening.setPassCount(vo.getPassCount());
        screening.setConditionCount(vo.getConditionCount());
        screening.setRejectCount(vo.getRejectCount());
        screening.setAdviceJson(buildAdvice(vo));
        screening.setVipFlag(client.getInvitedFlag() == null ? 0 : client.getInvitedFlag());
        screening.setStatus("GENERATED");
        screening.setCreatedAt(LocalDateTime.now());
        screeningMapper.insert(screening);

        // C4：落报告命中产品明细（员工陪访可见；客户侧永不展示产品名）
        saveScreeningProducts(screening.getReportNo(), vo);

        // P0-4：回填提交单（match_trace_no = 审计链路 UUID，status=MATCHED）
        if (submission != null) {
            submissionService.markMatched(submission.getSubmissionNo(), context.getTraceUuid());
        }
        return screening.getReportNo();
    }

    /**
     * 落匹配审计主表。
     *
     * @param clientProfileId 客户档案内部 ID（替换原硬编码 0L）
     * @param submissionId    提交单内部 ID（替换原硬编码 0L）
     */
    private Long saveTrace(String traceUuid, String customerGroup, MatchResultVO vo, long durationMs,
                           Long clientProfileId, Long submissionId) {
        MatchTrace trace = new MatchTrace();
        trace.setTraceUuid(traceUuid);
        trace.setClientProfileId(clientProfileId);
        trace.setSubmissionId(submissionId);
        trace.setCustomerGroup(customerGroup);
        trace.setTotalResult(resolveTotalResult(vo));
        trace.setHitCount(vo.getPassCount());
        trace.setStepCount(countSteps(vo));
        trace.setDurationMs(durationMs);
        trace.setMismatchFlag(0);
        trace.setExecutedAt(LocalDateTime.now());
        trace.setCreatedAt(LocalDateTime.now());
        matchTraceMapper.insert(trace);
        return trace.getId();
    }

    /**
     * 落报告命中产品明细（t_screening_product）。
     *
     * <p>match_score：产品下所有 step 中 PASS 占比 ×100（四舍五入）；
     * 无 step 明细时按命中结果兜底（PASS→90 / CONDITION→70 / REJECT→40）。
     *
     * @param reportNo 报告编号
     * @param vo       引擎匹配结果
     */
    private void saveScreeningProducts(String reportNo, MatchResultVO vo) {
        if (vo.getProducts() == null || vo.getProducts().isEmpty()) {
            return;
        }
        for (ProductMatchVO p : vo.getProducts()) {
            ScreeningProduct sp = new ScreeningProduct();
            sp.setReportNo(reportNo);
            sp.setBankProductId(p.getProductId());
            sp.setProductCode(p.getProductCode());
            sp.setHitResult(p.getTotalResult());
            sp.setMatchScore(resolveMatchScore(p));
            sp.setCreatedAt(LocalDateTime.now());
            screeningProductMapper.insert(sp);
        }
    }

    /** 产品匹配度：模块 step 命中率归一化 0-100 */
    private int resolveMatchScore(ProductMatchVO p) {
        int total = 0;
        int pass = 0;
        if (p.getModules() != null) {
            for (ModuleMatchVO m : p.getModules()) {
                if (m.getSteps() == null) {
                    continue;
                }
                for (StepMatchVO s : m.getSteps()) {
                    total++;
                    if ("PASS".equalsIgnoreCase(s.getStepResult())) {
                        pass++;
                    }
                }
            }
        }
        if (total > 0) {
            return (int) Math.round(pass * 100.0 / total);
        }
        if ("PASS".equalsIgnoreCase(p.getTotalResult())) {
            return 90;
        }
        if ("CONDITION".equalsIgnoreCase(p.getTotalResult())) {
            return 70;
        }
        return 40;
    }

    /**
     * 落规则日志。
     */
    private void saveRuleLogs(Long traceId, MatchResultVO vo) {
        if (vo.getProducts() == null) {
            return;
        }
        for (ProductMatchVO p : vo.getProducts()) {
            if (p.getModules() == null) {
                continue;
            }
            for (ModuleMatchVO m : p.getModules()) {
                if (m.getSteps() == null) {
                    continue;
                }
                for (StepMatchVO s : m.getSteps()) {
                    MatchRuleLog log = new MatchRuleLog();
                    log.setTraceId(traceId);
                    log.setPlanId(0L);
                    log.setModuleId(0L);
                    log.setStepId(0L);
                    log.setRuleCode(s.getRuleCode());
                    log.setExpression(s.getExpression());
                    log.setStepResult(s.getStepResult());
                    log.setHandlerStepResult(s.getStepResult());
                    log.setMismatchFlag(0);
                    log.setExecutedAt(LocalDateTime.now());
                    log.setCreatedAt(LocalDateTime.now());
                    matchRuleLogMapper.insert(log);
                }
            }
        }
    }

    /** 档位：优先用引擎结果，缺省按 PASS 数聚合。 */
    private Grade resolveGrade(MatchResultVO vo) {
        if (StringUtils.hasText(vo.getGrade())) {
            Grade g = Grade.fromCode(vo.getGrade());
            if (g != null) {
                return g;
            }
        }
        return new GradeAggregator().aggregate(vo.getPassCount());
    }

    /** 聚合产品级总结果。 */
    private String resolveTotalResult(MatchResultVO vo) {
        if (vo.getPassCount() > 0) {
            return "PASS";
        }
        if (vo.getConditionCount() > 0) {
            return "CONDITION";
        }
        if (vo.getRejectCount() > 0) {
            return "REJECT";
        }
        return "SKIP_SEGMENT_MISMATCH";
    }

    /** 统计执行步骤数。 */
    private int countSteps(MatchResultVO vo) {
        int n = 0;
        if (vo.getProducts() == null) {
            return n;
        }
        for (ProductMatchVO p : vo.getProducts()) {
            if (p.getModules() == null) {
                continue;
            }
            for (ModuleMatchVO m : p.getModules()) {
                n += m.getSteps() == null ? 0 : m.getSteps().size();
            }
        }
        return n;
    }

    /** 构建多维建议清单（避免承诺性表述）。 */
    private String buildAdvice(MatchResultVO vo) {
        Map<String, Object> advice = new LinkedHashMap<>();
        advice.put("summary", vo.getPassCount() > 0
                ? "匹配到 " + vo.getPassCount() + " 个可进件方向，建议优先沟通确认资质材料。"
                : vo.getConditionCount() > 0
                ? "部分方向需补充材料后重新评估，建议先补齐核心资质。"
                : "当前经营画像暂未匹配到合适方向，建议完善资料后再次评估。");
        advice.put("tips", new String[]{"以上为匹配程度分析，不构成银行通过承诺。", "实际结果以银行最终审批为准。"});
        return toJson(advice);
    }

    /** 简单 JSON 序列化（避免引入依赖）。 */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(escape((String) v)).append("\"");
            } else if (v instanceof String[]) {
                sb.append("[");
                String[] arr = (String[]) v;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append("\"").append(escape(arr[i])).append("\"");
                }
                sb.append("]");
            } else {
                sb.append(String.valueOf(v));
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
