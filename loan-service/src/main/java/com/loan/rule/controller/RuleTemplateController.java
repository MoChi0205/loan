package com.loan.rule.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.rule.entity.RuleCategory;
import com.loan.rule.entity.RuleTemplate;
import com.loan.rule.entity.RuleTemplateField;
import com.loan.rule.service.RuleTemplateService;
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

import java.util.List;
import java.util.Map;

/**
 * 规则模板 HTTP 接口（Web 管理端）：模板 CRUD + 字段编排 + 发布/下线 + 导入为规则。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/rule-template")
@RequiredArgsConstructor
public class RuleTemplateController {

    private final RuleTemplateService templateService;

    /** 分页查询模板 */
    @GetMapping("/page")
    public Result<PageResult<RuleTemplate>> page(
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(templateService.page(customerGroup, keyword, page, size));
    }

    /** 分类下拉 */
    @GetMapping("/categories")
    public Result<List<RuleCategory>> categories() {
        return Result.ok(templateService.categories());
    }

    /** 新建模板（草稿） */
    @PostMapping
    public Result<Long> create(@RequestBody RuleTemplate template, @CurrentUser LoanUser user) {
        return Result.ok(templateService.create(template, operatorName(user)));
    }

    /** 编辑模板 */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody RuleTemplate template,
                                 @CurrentUser LoanUser user) {
        template.setId(id);
        templateService.update(template, operatorName(user));
        return Result.ok("ok");
    }

    /** 删除模板（级联字段/版本） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok("ok");
    }

    /** 发布（生成版本快照 + 上线） */
    @PostMapping("/{id}/publish")
    public Result<String> publish(@PathVariable Long id, @CurrentUser LoanUser user) {
        templateService.publish(id, operatorName(user));
        return Result.ok("ok");
    }

    /** 下线 */
    @PostMapping("/{id}/offline")
    public Result<String> offline(@PathVariable Long id, @CurrentUser LoanUser user) {
        templateService.offline(id, operatorName(user));
        return Result.ok("ok");
    }

    /** 模板详情（模板 + 字段 + 版本） */
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(templateService.detail(id));
    }

    /** 导入为规则（fieldId 可选，缺省取第一个字段） */
    @PostMapping("/{id}/import")
    public Result<String> importToRule(@PathVariable Long id,
                                       @RequestParam(required = false) Long fieldId,
                                       @CurrentUser LoanUser user) {
        return Result.ok(templateService.importToRule(id, fieldId, operatorName(user)));
    }

    /** 新建字段定义 */
    @PostMapping("/field")
    public Result<Long> createField(@RequestBody RuleTemplateField field) {
        return Result.ok(templateService.createField(field));
    }

    /** 更新字段定义 */
    @PutMapping("/field/{id}")
    public Result<String> updateField(@PathVariable Long id, @RequestBody RuleTemplateField field) {
        field.setId(id);
        templateService.updateField(field);
        return Result.ok("ok");
    }

    /** 删除字段定义 */
    @DeleteMapping("/field/{id}")
    public Result<String> deleteField(@PathVariable Long id) {
        templateService.deleteField(id);
        return Result.ok("ok");
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
