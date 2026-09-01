package com.loan.lead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线索流转记录实体（t_lead_allocation_record，谁录入/认领/回收全程可追溯）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_lead_allocation_record")
public class LeadAllocationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 线索编号（业务唯一ID） */
    private String leadNo;

    /** 流转类型（MANUAL/AUTO/CLAIM/RECYCLE/TRANSFER/CONVERT） */
    private String actionType;

    /** 原归属人工号（业务编码） */
    private String fromStaffCode;

    /** 新归属人工号（业务编码） */
    private String toStaffCode;

    /** 操作人姓名 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
