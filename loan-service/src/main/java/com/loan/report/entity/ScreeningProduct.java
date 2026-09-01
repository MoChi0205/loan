package com.loan.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告命中产品明细（t_screening_product，员工陪访可见，客户永不展示产品名）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_screening_product")
public class ScreeningProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报告编号（业务唯一ID：report + 32 位随机） */
    private String reportNo;

    /** 银行产品 ID（t_bank_product.id） */
    private Long bankProductId;

    /** 产品编码（内部代号化） */
    private String productCode;

    /** 命中结果（PASS/CONDITION/REJECT） */
    private String hitResult;

    /** 匹配度 0-100（落库时按模块命中率计算） */
    private Integer matchScore;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
