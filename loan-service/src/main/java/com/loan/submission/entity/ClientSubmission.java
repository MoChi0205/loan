package com.loan.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户资料提交实体（t_client_submission，幂等防重）。
 *
 * <p>业务编码：submission_no（submit+32 位随机）、client_profile_code；match_trace_no 为
 * 匹配审计链路 UUID（业务编码，评审决策替代原 bigint match_trace_id），匹配完成后回填。
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_submission")
public class ClientSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交单号（业务ID：submit + 32 位随机） */
    private String submissionNo;

    /** 客户编码（业务ID） */
    private String clientProfileCode;

    /** 客群 */
    private String customerGroup;

    /** 提交资料结构化 JSON */
    private String dataJson;

    /** 客户端幂等键（防重复提交） */
    private String clientSubmitId;

    /** 关联匹配审计链路 UUID（业务编码，回填） */
    private String matchTraceNo;

    /** 状态（SUBMITTED/MATCHING/MATCHED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
