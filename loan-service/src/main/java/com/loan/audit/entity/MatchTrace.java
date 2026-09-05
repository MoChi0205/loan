package com.loan.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匹配审计主表实体（t_match_trace）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_match_trace")
public class MatchTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路 UUID */
    private String traceUuid;

    /** 客户档案 ID（调试场景为 0） */
    private Long clientProfileId;

    /** 提交单 ID（调试场景为 0） */
    private Long submissionId;

    /** 客群 */
    private String customerGroup;

    /** 总结果 */
    private String totalResult;

    /** 命中产品数 */
    private Integer hitCount;

    /** 执行步骤数 */
    private Integer stepCount;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 双结果不一致标记 */
    private Integer mismatchFlag;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
