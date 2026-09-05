package com.loan.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户回收规则配置（参考 tse / t_lead_recycle_config，全参数化不写死）。
 *
 * <p>全局单行配置（config_key=GLOBAL）：回收开关、回收天数（超期未跟进自动回收进公海）、
 * 预警天数（距回收剩余时站内预警归属人）、冷却天数（回收后原归属人不可认领）。</p>
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_recycle_config")
public class ClientRecycleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键（当前仅 GLOBAL 单行） */
    private String configKey;

    /** 回收开关（1 开 / 0 关） */
    private Integer recycleEnabled;

    /** 回收天数（超过该天数无跟进自动回收） */
    private Integer recycleDays;

    /** 预警天数（距回收剩余该天数时站内 + 订阅消息警告归属人） */
    private Integer warnDays;

    /** 冷却天数（回收后原归属人不可认领） */
    private Integer cooldownDays;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 回收开关是否开启。 */
    public boolean isRecycleEnabled() {
        return recycleEnabled != null && recycleEnabled == 1;
    }
}
