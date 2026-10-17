package com.loan.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.rule.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 规则 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface RuleMapper extends BaseMapper<Rule> {

    /**
     * 查询规则当前生效版本记录 id（t_rule.current_version → t_rule_version.id）。
     *
     * @param ruleId 规则 ID
     * @return 版本记录 id；无版本记录时返回 null
     */
    default Long selectCurrentVersionId(Long ruleId) {
        Rule rule = selectById(ruleId);
        if (rule == null || rule.getCurrentVersion() == null) return null;
        com.loan.rule.entity.RuleVersion version = com.loan.common.util.MapperAccess.query(
            com.loan.rule.entity.RuleVersion.class, mapper -> mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.loan.rule.entity.RuleVersion>()
                    .select(com.loan.rule.entity.RuleVersion::getId)
                    .eq(com.loan.rule.entity.RuleVersion::getRuleId, ruleId)
                    .eq(com.loan.rule.entity.RuleVersion::getVersionNo, rule.getCurrentVersion())));
        return version == null ? null : version.getId();
    }
}
