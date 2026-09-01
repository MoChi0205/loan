package com.loan.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.api.dto.rule.RuleDTO;
import com.loan.engine.catalog.RuleCatalog;
import com.loan.rule.entity.Rule;
import com.loan.rule.mapper.RuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则目录查询服务（供 RuleFacade 与后续 HTTP Controller 复用）。
 *
 * @author loan-platform
 */
@Service
public class RuleQueryService {

    @Resource
    private RuleMapper ruleMapper;

    /**
     * 查询规则目录（按客群过滤，仅上线规则）。
     *
     * @param customerGroup 客群（可为空）
     * @return 规则列表
     */
    public List<RuleDTO> listRules(String customerGroup) {
        LambdaQueryWrapper<Rule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Rule::getStatus, "ONLINE");
        if (customerGroup != null && !customerGroup.isEmpty() && !"COMMON".equalsIgnoreCase(customerGroup)) {
            wrapper.and(w -> w.eq(Rule::getCustomerGroup, customerGroup)
                    .or().eq(Rule::getCustomerGroup, "COMMON"));
        }
        wrapper.orderByAsc(Rule::getId);
        return ruleMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 管理端查询规则目录（不过滤 ONLINE，支持客群 / 状态筛选）。
     *
     * @param customerGroup 客群（可选）
     * @param status        状态（可选）
     * @return 规则列表
     */
    public List<RuleDTO> listRulesForAdmin(String customerGroup, String status) {
        LambdaQueryWrapper<Rule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerGroup) && !"COMMON".equalsIgnoreCase(customerGroup)) {
            wrapper.and(w -> w.eq(Rule::getCustomerGroup, customerGroup)
                    .or().eq(Rule::getCustomerGroup, "COMMON"));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Rule::getStatus, status);
        }
        wrapper.orderByAsc(Rule::getId);
        return ruleMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 实体 → DTO 转换（分类名从 RuleCatalog 目录匹配）。
     */
    private RuleDTO toDTO(Rule r) {
        RuleDTO dto = new RuleDTO();
        dto.setRuleCode(r.getRuleCode());
        dto.setRuleName(r.getRuleName());
        dto.setFieldCode(r.getFieldCode());
        dto.setOperator(r.getOperator());
        dto.setValueText(r.getValueText());
        dto.setExpression(r.getFieldCode() + " " + r.getOperator() + " " + (r.getValueText() == null ? "" : r.getValueText()));
        dto.setCustomerGroup(r.getCustomerGroup());
        dto.setDescription(r.getDescription());
        dto.setStatus(r.getStatus());

        RuleCatalog catalog = RuleCatalog.fromCode(r.getRuleCode());
        if (catalog != null) {
            dto.setCategoryCode(catalog.getCategory().getCode());
            dto.setCategoryName(catalog.getCategory().getName());
        }
        return dto;
    }
}
