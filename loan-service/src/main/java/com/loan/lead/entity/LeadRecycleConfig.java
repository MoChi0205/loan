package com.loan.lead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_lead_recycle_config")
public class LeadRecycleConfig implements Serializable {
    @TableId(type = IdType.AUTO) private Long id;
    private String followStatus;
    private Integer recycleEnabled;
    private Integer recycleDays;
    private Integer warnDays;
    private Integer cooldownDays;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
