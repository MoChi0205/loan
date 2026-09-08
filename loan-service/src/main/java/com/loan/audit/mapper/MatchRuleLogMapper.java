package com.loan.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.audit.entity.MatchRuleLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 匹配规则日志 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface MatchRuleLogMapper extends BaseMapper<MatchRuleLog> {

    /**
     * 批量落匹配规则日志（一次多值 INSERT，消除三层嵌套循环内的单条 insert 的 N+1）。
     * 主键由数据库自增生成；调用前需保证 list 非空。
     *
     * @param list 规则日志列表
     * @return 受影响行数
     */
    @Insert({"<script>INSERT INTO t_match_rule_log "
            + "(trace_id, plan_id, module_id, step_id, rule_code, expression, step_result, handler_step_result, mismatch_flag, executed_at, created_at) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.traceId}, #{item.planId}, #{item.moduleId}, #{item.stepId}, #{item.ruleCode}, #{item.expression}, "
            + "#{item.stepResult}, #{item.handlerStepResult}, #{item.mismatchFlag}, #{item.executedAt}, #{item.createdAt})",
            "</foreach></script>"})
    int insertBatch(@Param("list") List<MatchRuleLog> list);
}
