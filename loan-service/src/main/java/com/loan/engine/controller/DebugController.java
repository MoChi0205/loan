package com.loan.engine.controller;

import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import com.loan.common.Result;
import com.loan.engine.dto.DebugMatchRequest;
import com.loan.engine.dto.MatchResultVO;
import com.loan.engine.dto.ModuleMatchVO;
import com.loan.engine.dto.ProductMatchVO;
import com.loan.engine.dto.StepMatchVO;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.execute.ProductPlan;
import com.loan.engine.service.MatchService;
import com.loan.plan.service.PlanLoaderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 调试中心接口（调试复用生产引擎，可选落库审计）。
 *
 * <p>选渠道 + 模拟客户 → 运行规则引擎（计划按渠道从 DB 组装）→ 返回步骤级结果树；
 * 结果落库 t_match_trace / t_match_rule_log，供审计中心按 traceUuid 追踪全链路。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    /** 计划加载服务（从 DB 按渠道组装） */
    @Resource
    private PlanLoaderService planLoaderService;

    /** 匹配编排服务 */
    @Resource
    private MatchService matchService;

    /** 匹配审计主表 Mapper */
    @Resource
    private MatchTraceMapper matchTraceMapper;

    /** 匹配规则日志 Mapper */
    @Resource
    private MatchRuleLogMapper matchRuleLogMapper;

    /**
     * 调试执行匹配并落库审计。
     *
     * @param request 模拟客户（渠道 + 客群 + 经营事实）
     * @return 匹配结果树（含 traceUuid）
     */
    @PostMapping("/match")
    public Result<MatchResultVO> match(@RequestBody DebugMatchRequest request) {
        CustomerGroup group = CustomerGroup.fromCode(request.getCustomerGroup());
        if (group == null) {
            group = CustomerGroup.ENTERPRISE;
        }
        AdmissionContext context = AdmissionContext.builder()
                .traceUuid(UUID.randomUUID().toString().replace("-", ""))
                .customerGroup(group)
                .channelCode(request.getChannelCode())
                .fieldValues(request.getFacts())
                .build();

        long start = System.currentTimeMillis();
        // 从 DB 按渠道 + 申请城市组装产品-计划
        List<ProductPlan> productPlans = planLoaderService.loadProductPlans(request.getChannelCode(), group, request.getApplyCity());
        MatchResultVO vo = matchService.match(context, productPlans);
        long duration = System.currentTimeMillis() - start;

        // 落库审计（trace + rule logs）
        Long traceId = saveTrace(context.getTraceUuid(), group.getCode(), vo, duration);

        // 回填 traceUuid 供前端跳审计中心
        vo.setTraceUuid(context.getTraceUuid());
        saveRuleLogs(traceId, vo);
        return Result.ok(vo);
    }

    /**
     * 落库匹配审计主表。
     *
     * @param traceUuid     链路 UUID
     * @param customerGroup 客群
     * @param vo            匹配结果
     * @param durationMs    耗时
     * @return trace 主键
     */
    private Long saveTrace(String traceUuid, String customerGroup, MatchResultVO vo, long durationMs) {
        MatchTrace trace = new MatchTrace();
        trace.setTraceUuid(traceUuid);
        trace.setClientProfileId(0L);
        trace.setSubmissionId(0L);
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
     * 落库规则日志。
     *
     * @param traceId 审计主表 ID
     * @param vo      匹配结果
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

    /**
     * 聚合产品级总结果：有 PASS 则 PASS，否则 CONDITION，否则 REJECT。
     *
     * @param vo 匹配结果
     * @return 总结果编码
     */
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

    /**
     * 统计执行步骤数。
     *
     * @param vo 匹配结果
     * @return 步骤总数
     */
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
}
