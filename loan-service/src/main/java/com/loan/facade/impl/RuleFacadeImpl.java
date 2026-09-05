package com.loan.facade.impl;

import com.loan.api.dto.Result;
import com.loan.api.dto.rule.RuleDTO;
import com.loan.api.facade.RuleFacade;
import com.loan.rule.service.RuleQueryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 规则目录服务实现（开发阶段 {@code @Service}；跨系统 Dubbo 暴露时改 {@code @DubboService}）。
 *
 * @author loan-platform
 */
@Service
public class RuleFacadeImpl implements RuleFacade {

    @Resource
    private RuleQueryService ruleQueryService;

    @Override
    public Result<List<RuleDTO>> listRules(String customerGroup) {
        try {
            return Result.ok(ruleQueryService.listRules(customerGroup));
        } catch (Exception e) {
            return Result.fail(4000, "规则查询失败: " + e.getMessage());
        }
    }
}
