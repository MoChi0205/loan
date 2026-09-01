package com.loan.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短信模板实体（t_sms_template，三类场景）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_sms_template")
public class SmsTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 模板内容（变量占位符如 ${code}） */
    private String content;

    /** 变量占位符定义 */
    private String paramsJson;

    /** 短信签名 */
    private String signName;

    /** 短信类型（LOGIN_VERIFY/NOTIFICATION/MARKETING） */
    private String smsType;

    /** 腾讯云模板 ID */
    private String providerTemplateId;

    /** 频控策略 */
    private String freqStrategy;

    /** 营销短信强制退订标记 */
    private Integer unsubscribeRequired;

    /** 启停开关 */
    private Integer enabled;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
