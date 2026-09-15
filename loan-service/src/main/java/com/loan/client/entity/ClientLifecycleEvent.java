package com.loan.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客户归属、公海与首次跟进生命周期事件。 */
@Data
@TableName("t_client_lifecycle_event")
public class ClientLifecycleEvent implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientCode;
    private String eventType;
    private String staffCode;
    private String seaLevel;
    private String seaDeptCode;
    private Integer episodeNo;
    private LocalDateTime eventAt;
    private String operatorStaffCode;
    private String reasonCode;
}
