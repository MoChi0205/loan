package com.loan.engine.service;

import com.loan.engine.aggregate.GradeAggregator;
import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.dto.MatchResultVO;
import com.loan.engine.dto.ModuleMatchVO;
import com.loan.engine.dto.ProductMatchVO;
import com.loan.engine.dto.StepMatchVO;
import com.loan.engine.enums.StepResult;
import com.loan.engine.enums.TotalResult;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.execute.AdmissionPlanExecutor;
import com.loan.engine.execute.PlanExecutionResult;
import com.loan.engine.execute.PlanModule;
import com.loan.engine.execute.ProductPlan;
import com.loan.engine.execute.StepExecutionRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 匹配编排服务（规则引擎门面）。
 *
 * <p>职责：执行产品计划 → 档位聚合 → 组装结果树 VO（按模块分组步骤）。
 * 审计落库在正式匹配链路（t_match_trace / t_match_rule_log），调试中心影子执行不落线上。
 *
 * @author loan-platform
 */
@Service
public class MatchService {

    /** 计划执行器 */
    @Resource
    private AdmissionPlanExecutor executor;

    /** 档位聚合器 */
    @Resource
    private GradeAggregator gradeAggregator;

    /**
     * 执行匹配并返回结果树。
     *
     * @param context      执行上下文（客户事实）
     * @param productPlans 产品-计划绑定列表
     * @return 匹配结果总览
     */
    public MatchResultVO match(AdmissionContext context, List<ProductPlan> productPlans) {
        List<PlanExecutionResult> results = executor.execute(context, productPlans);

        // 建立 moduleId → PlanModule 元信息映射（logicType / globalPre）
        Map<Long, PlanModule> moduleMap = buildModuleMap(productPlans);

        int pass = 0;
        int condition = 0;
        int reject = 0;
        for (PlanExecutionResult r : results) {
            TotalResult tr = r.getTotalResult();
            if (tr == TotalResult.PASS) {
                pass++;
            } else if (tr == TotalResult.CONDITION) {
                condition++;
            } else if (tr == TotalResult.REJECT) {
                reject++;
            }
        }

        MatchResultVO vo = new MatchResultVO();
        vo.setGrade(gradeAggregator.aggregate(pass).getCode());
        vo.setBankCount(pass);
        vo.setProductCount(results.size());
        vo.setPassCount(pass);
        vo.setConditionCount(condition);
        vo.setRejectCount(reject);

        for (PlanExecutionResult r : results) {
            vo.getProducts().add(toProductVO(r, moduleMap));
        }
        return vo;
    }

    /**
     * 建立 moduleId → PlanModule 元信息映射。
     *
     * @param productPlans 产品-计划列表
     * @return moduleId → 模块元信息
     */
    private Map<Long, PlanModule> buildModuleMap(List<ProductPlan> productPlans) {
        Map<Long, PlanModule> map = new HashMap<>();
        if (productPlans == null) {
            return map;
        }
        for (ProductPlan pp : productPlans) {
            for (PlanModule module : pp.getPlan().getModules()) {
                map.put(module.getModuleId(), module);
            }
        }
        return map;
    }

    /**
     * 组装产品结果 VO（按模块分组步骤）。
     *
     * @param result    产品执行结果
     * @param moduleMap 模块元信息映射
     * @return 产品结果 VO
     */
    private ProductMatchVO toProductVO(PlanExecutionResult result, Map<Long, PlanModule> moduleMap) {
        ProductMatchVO pvo = new ProductMatchVO();
        pvo.setProductId(result.getProductId());
        pvo.setProductCode(result.getProductCode());
        pvo.setProductName(result.getProductName());
        pvo.setTotalResult(result.getTotalResult().getCode());

        Map<Long, ModuleMatchVO> moduleVoMap = new LinkedHashMap<>();
        for (StepExecutionRecord rec : result.getStepRecords()) {
            ModuleMatchVO mvo = moduleVoMap.get(rec.getModuleId());
            if (mvo == null) {
                mvo = new ModuleMatchVO();
                mvo.setModuleCode(rec.getModuleCode());
                mvo.setModuleName(rec.getModuleName());
                PlanModule pm = moduleMap.get(rec.getModuleId());
                mvo.setLogicType(pm != null ? pm.getLogicType() : "AND");
                mvo.setGlobalPre(pm != null && pm.isGlobalPre());
                moduleVoMap.put(rec.getModuleId(), mvo);
            }
            StepMatchVO svo = new StepMatchVO();
            svo.setRuleCode(rec.getRuleCode());
            svo.setRuleName(resolveRuleName(rec.getRuleCode()));
            svo.setFieldCode(rec.getFieldCode());
            svo.setExpression(rec.getExpression());
            svo.setStepResult(rec.getStepResult().getCode());
            svo.setDetail(rec.getDetail());
            mvo.getSteps().add(svo);
        }
        for (ModuleMatchVO mvo : moduleVoMap.values()) {
            mvo.setModulePassed(calcModulePassed(mvo));
        }
        pvo.setModules(new ArrayList<>(moduleVoMap.values()));
        return pvo;
    }

    /**
     * 解析规则名称（目录内取展示名，自定义规则取 ruleCode）。
     *
     * @param ruleCode 规则编码
     * @return 规则名称
     */
    private String resolveRuleName(String ruleCode) {
        RuleCatalog catalog = RuleCatalog.fromCode(ruleCode);
        return catalog != null ? catalog.getDisplayName() : ruleCode;
    }

    /**
     * 计算模块是否通过：AND 模块无 FAIL/ERROR 即通过；OR 模块有 PASS 即通过。
     *
     * @param mvo 模块 VO
     * @return true 通过
     */
    private boolean calcModulePassed(ModuleMatchVO mvo) {
        if ("OR".equalsIgnoreCase(mvo.getLogicType())) {
            for (StepMatchVO step : mvo.getSteps()) {
                if (StepResult.PASS.getCode().equals(step.getStepResult())) {
                    return true;
                }
            }
            return false;
        }
        for (StepMatchVO step : mvo.getSteps()) {
            if (StepResult.FAIL.getCode().equals(step.getStepResult())
                    || StepResult.ERROR.getCode().equals(step.getStepResult())) {
                return false;
            }
        }
        return true;
    }
}
