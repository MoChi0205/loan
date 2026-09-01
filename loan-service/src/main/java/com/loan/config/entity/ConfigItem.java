package com.loan.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方服务配置项（t_config）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_config")
public class ConfigItem {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置组（TENCENT_OCR/SMS/WECOM 等） */
    private String configGroup;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 密钥标记（1=敏感，后台脱敏） */
    private Integer isSecret;

    /** 说明 */
    private String description;

    /** 启停 */
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
