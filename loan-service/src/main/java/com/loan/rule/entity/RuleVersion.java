package com.loan.rule.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/** 规则版本轻量映射，查询不加载历史快照内容。 */
@Data
@TableName("t_rule_version")
public class RuleVersion {
    @TableId private Long id;
    private Long ruleId;
    private Integer versionNo;
}
