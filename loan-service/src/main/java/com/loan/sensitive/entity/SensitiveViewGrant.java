package com.loan.sensitive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感数据查看授权实体（t_sensitive_view_grant，受限角色申请后授权）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_sensitive_view_grant")
public class SensitiveViewGrant implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人工号（staff_code） */
    private String userNo;

    /** 线索业务 ID */
    private String leadNo;
    /** 客户档案业务编码（客户手机号查看授权）；与 leadNo 二选一。 */
    private String clientCode;

    /** 授权时间 */
    private LocalDateTime createdAt;
}
