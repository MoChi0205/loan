package com.loan.api.facade;

import com.loan.api.dto.Result;
import com.loan.api.dto.rule.RuleDTO;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 规则目录服务契约（Dubbo 跨系统接口）。
 *
 * @author loan-platform
 */
@DubboService(version = "1.0.0")
public interface RuleFacade {

    /**
     * 查询规则目录（可按客群过滤）。
     *
     * @param customerGroup 客群（可选，为空查全部）
     * @return 规则列表
     */
    Result<List<RuleDTO>> listRules(String customerGroup);
}
