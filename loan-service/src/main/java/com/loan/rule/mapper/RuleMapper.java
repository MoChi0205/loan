package com.loan.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.rule.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT rv.id FROM t_rule_version rv INNER JOIN t_rule r ON r.id = rv.rule_id "
            + "WHERE rv.rule_id = #{ruleId} AND rv.version_no = r.current_version "
            + "ORDER BY rv.id DESC LIMIT 1")
    Long selectCurrentVersionId(@Param("ruleId") Long ruleId);
}
