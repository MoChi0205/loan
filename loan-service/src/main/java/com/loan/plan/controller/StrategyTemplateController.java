package com.loan.plan.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.plan.entity.StrategyTemplate;
import com.loan.plan.entity.StrategyTemplateModule;
import com.loan.plan.entity.StrategyTemplateStep;
import com.loan.plan.service.StrategyTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * 策略模版 HTTP 接口（Web 管理端）：模版 CRUD + 模块/步骤编排 + 上线/下线。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/strategy-template")
@RequiredArgsConstructor
public class StrategyTemplateController {

    private final StrategyTemplateService templateService;

    /**
     * 分页查询模版。
     */
    @GetMapping("/page")
    public Result<PageResult<StrategyTemplate>> page(
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(templateService.page(customerGroup, keyword, status, page, size));
    }

    /** 按模板业务编码批量查询（去重、保序，最多100条）。 */
    @PostMapping("/batch")
    public Result<List<StrategyTemplate>> batch(@RequestBody List<String> templateCodes) {
        return Result.ok(templateService.listByCodes(templateCodes));
    }

    /**
     * 新建模版（草稿）。
     */
    @PostMapping
    public Result<String> create(@RequestBody StrategyTemplate template, @CurrentUser LoanUser user) {
        return Result.ok(templateService.create(template, operatorName(user)));
    }

    /**
     * 编辑模版。
     */
    @PutMapping("/{templateCode}")
    public Result<String> update(@PathVariable("templateCode") String templateCode, @RequestBody StrategyTemplate template,
                                 @CurrentUser LoanUser user) {
        template.setTemplateCode(templateCode);
        templateService.update(template, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 删除模版（级联）。
     */
    @DeleteMapping("/{templateCode}")
    public Result<String> delete(@PathVariable("templateCode") String templateCode) {
        templateService.delete(templateCode);
        return Result.ok("ok");
    }

    /**
     * 上线（发布）。
     */
    @PostMapping("/{templateCode}/publish")
    public Result<String> publish(@PathVariable("templateCode") String templateCode, @CurrentUser LoanUser user) {
        templateService.publish(templateCode, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 下线。
     */
    @PostMapping("/{templateCode}/offline")
    public Result<String> offline(@PathVariable("templateCode") String templateCode, @CurrentUser LoanUser user) {
        templateService.offline(templateCode, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 模版详情（模版 + 模块 + 步骤）。
     */
    @GetMapping("/{templateCode}/detail")
    public Result<Map<String, Object>> detail(@PathVariable("templateCode") String templateCode) {
        return Result.ok(templateService.detail(templateCode));
    }

    /**
     * 新建模块。
     */
    @PostMapping("/module")
    public Result<Long> createModule(@RequestBody StrategyTemplateModule module) {
        return Result.ok(templateService.createModule(module));
    }

    /**
     * 更新模块。
     */
    @PutMapping("/module/{id}")
    public Result<String> updateModule(@PathVariable Long id, @RequestBody StrategyTemplateModule module) {
        module.setId(id);
        templateService.updateModule(module);
        return Result.ok("ok");
    }

    /**
     * 删除模块（级联步骤）。
     */
    @DeleteMapping("/module/{id}")
    public Result<String> deleteModule(@PathVariable Long id) {
        templateService.deleteModule(id);
        return Result.ok("ok");
    }

    /**
     * 新建步骤。
     */
    @PostMapping("/step")
    public Result<Long> createStep(@RequestBody StrategyTemplateStep step) {
        return Result.ok(templateService.createStep(step));
    }

    /**
     * 更新步骤。
     */
    @PutMapping("/step/{id}")
    public Result<String> updateStep(@PathVariable Long id, @RequestBody StrategyTemplateStep step) {
        step.setId(id);
        templateService.updateStep(step);
        return Result.ok("ok");
    }

    /**
     * 删除步骤。
     */
    @DeleteMapping("/step/{id}")
    public Result<String> deleteStep(@PathVariable Long id) {
        templateService.deleteStep(id);
        return Result.ok("ok");
    }

    /**
     * 渠道策略生成模版快照：把渠道下某策略的执行计划深拷贝为草稿态模版。
     */
    @PostMapping("/snapshot-from-channel")
    public Result<Long> snapshotFromChannel(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        return Result.ok(templateService.snapshotFromChannel(body.get("channelCode"), body.get("strategyCode"),
                body.get("templateCode"), body.get("templateName"), operatorName(user)));
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
