package com.loan.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短信发送记录实体（t_sms_record，全量落库）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_sms_record")
public class SmsRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号（AES 加密） */
    private String phone;

    /** 手机号 SHA-256 哈希 */
    private String phoneHash;

    /** 短信类型 */
    private String smsType;

    /** 模板编码（业务编码） */
    private String templateCode;

    /** 变量快照 */
    private String paramsSnapshot;

    /** 发送内容快照 */
    private String content;

    /** 发送通道 */
    private String channelCode;

    /** 状态（PENDING/SENT/SUCCESS/FAIL） */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 操作人 */
    private String operator;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
