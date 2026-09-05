package com.loan.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 合作银行渠道实体（t_bank_channel）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_bank_channel")
public class BankChannel {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码 */
    private String channelCode;

    /** 银行名称 */
    private String bankName;

    /** 状态（ACTIVE/SUSPENDED） */
    private String status;
}
