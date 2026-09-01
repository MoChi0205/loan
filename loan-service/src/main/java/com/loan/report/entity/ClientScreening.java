package com.loan.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 初筛报告实体（t_client_screening，档位+数量展示，不含银行/产品名称）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_screening")
public class ClientScreening implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报告编号（业务唯一ID：report + 32 位随机） */
    private String reportNo;

    /** 客户编码（业务唯一ID） */
    private String clientProfileCode;

    /** 匹配审计 UUID */
    private String matchTraceUuid;

    /** 报告模板编码 */
    private String templateCode;

    /** 档位（HIGH/MIDDLE/LOW） */
    private String grade;

    /** 预计可进件银行数 */
    private Integer bankCount;

    /** 命中产品数 */
    private Integer productCount;

    /** PASS 数 */
    private Integer passCount;

    /** CONDITION 需补料数 */
    private Integer conditionCount;

    /** REJECT 数 */
    private Integer rejectCount;

    /** 多维建议清单 JSON */
    private String adviceJson;

    /** 报告文件 key（水印版） */
    private String reportFileKey;

    /** VIP 版标记 */
    private Integer vipFlag;

    /** 状态（GENERATED/VIEWED） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
