package com.loan.sensitive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 敏感数据查看留痕实体（t_sensitive_view_log，日限额统计依据）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_sensitive_view_log")
public class SensitiveViewLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 查看人工号 */
    private String userNo;

    /** 线索业务 ID */
    private String leadNo;

    /** 查看日期（日限额统计） */
    private LocalDate viewDate;

    /** 查看时间 */
    private LocalDateTime createdAt;
}
