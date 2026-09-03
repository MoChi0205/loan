package com.loan.plan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.plan.entity.AdmissionExecutionPlan;
import com.loan.plan.entity.AdmissionPlanModule;
import com.loan.plan.entity.AdmissionPlanStep;
import com.loan.plan.mapper.AdmissionExecutionPlanMapper;
import com.loan.plan.service.PlanOrchestrationService;
import com.loan.plan.service.StrategyTemplateService;
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
 * 执行计划 HTTP 接口（Web 管理端）：计划列表 + 计划/模块/步骤编排 CRUD。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/execution-plan")
@RequiredArgsConstructor
public class AdmissionExecutionPlanController {

    private final AdmissionExecutionPlanMapper planMapper;
    private final PlanOrchestrationService orchestrationService;
    private final StrategyTemplateService templateService;

    /**
     * 计划列表（供策略绑定下拉）。
     */
    @GetMapping("/list")
    public Result<List<AdmissionExecutionPlan>> list() {
        return Result.ok(planMapper.selectList(
                new LambdaQueryWrapper<AdmissionExecutionPlan>().orderByDesc(AdmissionExecutionPlan::getId)));
    }

    /**
     * 计划详情（计划 + 模块 + 步骤）。
     */
    @GetMapping("/{planCode}/detail")
    public Result<Map<String, Object>> detail(@PathVariable("planCode") String planCode) {
        return Result.ok(orchestrationService.detail(planCode));
    }

    /**
     * 新建计划。
     */
    @PostMapping
    public Result<String> createPlan(@RequestBody AdmissionExecutionPlan plan, @CurrentUser LoanUser user) {
        return Result.ok(orchestrationService.createPlan(plan, operatorName(user)));
    }

    /**
     * 更新计划。
     */
    @PutMapping("/{planCode}")
    public Result<String> updatePlan(@PathVariable("planCode") String planCode, @RequestBody AdmissionExecutionPlan plan,
                                     @CurrentUser LoanUser user) {
        orchestrationService.updatePlan(planCode, plan, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 删除计划（级联模块/步骤）。
     */
    @DeleteMapping("/{planCode}")
    public Result<String> deletePlan(@PathVariable("planCode") String planCode) {
        orchestrationService.deletePlan(planCode);
        return Result.ok("ok");
    }

    /**
     * 新建模块。
     */
    @PostMapping("/module")
    public Result<Long> createModule(@RequestBody AdmissionPlanModule module, @CurrentUser LoanUser user) {
        return Result.ok(orchestrationService.createModule(module, operatorName(user)));
    }

    /**
     * 更新模块。
     */
    @PutMapping("/module/{id}")
    public Result<String> updateModule(@PathVariable Long id, @RequestBody AdmissionPlanModule module) {
        module.setId(id);
        orchestrationService.updateModule(module, null);
        return Result.ok("ok");
    }

    /**
     * 删除模块（级联步骤）。
     */
    @DeleteMapping("/module/{id}")
    public Result<String> deleteModule(@PathVariable Long id) {
        orchestrationService.deleteModule(id);
        return Result.ok("ok");
    }

    /**
     * 新建步骤。
     */
    @PostMapping("/step")
    public Result<Long> createStep(@RequestBody AdmissionPlanStep step, @CurrentUser LoanUser user) {
        return Result.ok(orchestrationService.createStep(step, operatorName(user)));
    }

    /**
     * 更新步骤。
     */
    @PutMapping("/step/{id}")
    public Result<String> updateStep(@PathVariable Long id, @RequestBody AdmissionPlanStep step) {
        step.setId(id);
        orchestrationService.updateStep(step, null);
        return Result.ok("ok");
    }

    /**
     * 删除步骤。
     */
    @DeleteMapping("/step/{id}")
    public Result<String> deleteStep(@PathVariable Long id) {
        orchestrationService.deleteStep(id);
        return Result.ok("ok");
    }

    /**
     * 另存为模版：把执行计划深拷贝为策略模版（草稿态）。
     */
    @PostMapping("/save-as-template")
    public Result<String> saveAsTemplate(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        return Result.ok(templateService.saveAsTemplateFromPlan(body.get("planCode"), body.get("templateCode"),
                body.get("templateName"), operatorName(user)));
    }

    /**
     * 应用模版：把策略模版实例化为执行计划（草稿态）。
     */
    @PostMapping("/apply-template")
    public Result<String> applyTemplate(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        return Result.ok(orchestrationService.applyTemplate(body.get("templateCode"), body.get("planCode"),
                body.get("planName"), operatorName(user)));
    }

    /**
     * 复制计划：计划 + 模块 + 步骤 深拷贝为新草稿计划。
     */
    @PostMapping("/copy")
    public Result<String> copyPlan(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        return Result.ok(orchestrationService.copyPlan(body.get("planCode"), operatorName(user)));
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
