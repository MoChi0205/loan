package com.loan.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.rule.dto.RuleSaveReq;
import com.loan.rule.entity.Rule;
import com.loan.rule.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 规则管理写服务（新增 / 编辑 / 删除 / 详情 / 批量启停）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleMapper ruleMapper;

    /**
     * 新增规则。
     *
     * @param req      请求
     * @param operator 操作人姓名
     * @return 规则 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(RuleSaveReq req, String operator) {
        validate(req);
        if (ruleMapper.selectCount(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getRuleCode, req.getRuleCode())) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则编码已存在");
        }
        Rule rule = toEntity(req);
        rule.setCreatedBy(operator);
        ruleMapper.insert(rule);
        return rule.getRuleCode();
    }

    /**
     * 编辑规则。
     *
     * @param req      请求（含 id）
     * @param operator 操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(RuleSaveReq req, String operator) {
        if (!StringUtils.hasText(req.getRuleCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则编码必填");
        }
        Rule exist = ruleMapper.selectOne(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getRuleCode, req.getRuleCode()));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "规则不存在");
        }
        Rule rule = toEntity(req);
        rule.setId(exist.getId());
        rule.setUpdatedBy(operator);
        ruleMapper.updateById(rule);
    }

    /**
     * 规则详情（按业务唯一编码）。
     *
     * @param ruleCode 规则编码
     * @return 规则实体
     */
    public Rule getByCode(String ruleCode) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<Rule>().eq(Rule::getRuleCode, ruleCode));
    }

    /**
     * 删除规则（按业务唯一编码）。
     *
     * @param ruleCode 规则编码
     * @param operator 操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByCode(String ruleCode, String operator) {
        Rule exist = ruleMapper.selectOne(new LambdaQueryWrapper<Rule>().eq(Rule::getRuleCode, ruleCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "规则不存在");
        }
        ruleMapper.deleteById(exist.getId());
    }

    /**
     * 批量更新状态（启用 / 停用）。
     *
     * @param ids      规则 ID 列表
     * @param status   目标状态
     * @param operator 操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(List<String> ruleCodes, String status, String operator) {
        if (ruleCodes == null || ruleCodes.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<Rule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Rule::getRuleCode, ruleCodes).set(Rule::getStatus, status).set(Rule::getUpdatedBy, operator);
        ruleMapper.update(null, wrapper);
    }

    /**
     * 校验必填项。
     */
    private void validate(RuleSaveReq req) {
        if (!StringUtils.hasText(req.getRuleCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则编码必填");
        }
        if (!StringUtils.hasText(req.getRuleName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则名称必填");
        }
        if (!StringUtils.hasText(req.getFieldCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段编码必填");
        }
        if (!StringUtils.hasText(req.getOperator())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "运算符必填");
        }
        if (!StringUtils.hasText(req.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态必填");
        }
    }

    /**
     * 请求 → 实体（不含 id）。
     */
    private Rule toEntity(RuleSaveReq req) {
        Rule rule = new Rule();
        rule.setRuleCode(req.getRuleCode());
        rule.setRuleName(req.getRuleName());
        rule.setFieldCode(req.getFieldCode());
        rule.setFieldName(req.getFieldName());
        rule.setOperator(req.getOperator());
        rule.setValueType(req.getValueType());
        rule.setValueText(req.getValueText());
        rule.setCustomerGroup(req.getCustomerGroup());
        rule.setDescription(req.getDescription());
        rule.setStatus(req.getStatus());
        return rule;
    }
}
