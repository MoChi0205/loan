package com.loan.rule.controller;

import com.loan.api.dto.rule.RuleDTO;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.rule.dto.RuleBatchStatusReq;
import com.loan.rule.dto.RuleSaveReq;
import com.loan.rule.entity.Rule;
import com.loan.rule.service.RuleQueryService;
import com.loan.rule.service.RuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 规则目录 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/rule")
public class RuleController {

    @Resource
    private RuleQueryService ruleQueryService;

    @Resource
    private RuleService ruleService;

    /**
     * 查询规则目录（管理端：不过滤 ONLINE，支持客群 / 状态筛选）。
     *
     * @param customerGroup 客群（可选）
     * @param status        状态（可选）
     * @return 规则列表
     */
    @GetMapping("/list")
    public Result<List<RuleDTO>> list(
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String status) {
        return Result.ok(ruleQueryService.listRulesForAdmin(customerGroup, status));
    }

    /**
     * 新增规则。
     *
     * @param req  请求
     * @param user 当前用户
     * @return 规则编码（业务唯一ID）
     */
    @PostMapping
    @OpLog(bizType = "规则", action = "CREATE")
    public Result<String> create(@RequestBody RuleSaveReq req, @CurrentUser LoanUser user) {
        return Result.ok(ruleService.create(req, user == null ? "system" : user.getName()));
    }

    /**
     * 编辑规则。
     *
     * @param req  请求（含 ruleCode）
     * @param user 当前用户
     * @return 成功标记
     */
    @PutMapping
    @OpLog(bizType = "规则", action = "UPDATE")
    public Result<Void> update(@RequestBody RuleSaveReq req, @CurrentUser LoanUser user) {
        ruleService.update(req, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 规则详情。
     *
     * @param ruleCode 规则编码（业务唯一ID）
     * @return 规则实体
     */
    @GetMapping("/{ruleCode}")
    public Result<Rule> get(@PathVariable String ruleCode) {
        return Result.ok(ruleService.getByCode(ruleCode));
    }

    /**
     * 删除规则。
     *
     * @param ruleCode 规则编码（业务唯一ID）
     * @param user     当前用户
     * @return 成功标记
     */
    @DeleteMapping("/{ruleCode}")
    @OpLog(bizType = "规则", action = "DELETE")
    public Result<Void> delete(@PathVariable String ruleCode, @CurrentUser LoanUser user) {
        ruleService.deleteByCode(ruleCode, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 批量更新状态（启用 / 停用）。
     *
     * @param req  请求（ruleCodes + status）
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/batch-status")
    @OpLog(bizType = "规则", action = "ENABLE")
    public Result<Void> batchStatus(@RequestBody RuleBatchStatusReq req, @CurrentUser LoanUser user) {
        ruleService.batchUpdateStatus(req.getRuleCodes(), req.getStatus(), user == null ? "system" : user.getName());
        return Result.ok();
    }
}
