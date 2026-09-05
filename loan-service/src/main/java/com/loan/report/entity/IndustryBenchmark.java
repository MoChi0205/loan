package com.loan.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 行业基准均值实体（t_industry_benchmark，经营诊断多维统计对比基线）。
 *
 * <p>替代 {@code MiniMatchService} 中硬编码的行业均值常量：按
 * 行业编码 × 客群 × 维度 × 统计周期 存储均值，DEFAULT 行兜底。
 * 业务 ID：benchmark + 32 位随机（运行时走 {@code BizIdGenerator}）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_industry_benchmark")
public class IndustryBenchmark implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基准编号（业务唯一ID：benchmark + 32 位随机） */
    private String benchmarkNo;

    /** 行业编码（GB/T4754 门类，如 C=制造业；DEFAULT=全行业兜底） */
    private String industryCode;

    /** 行业名称（用于自由文本归一化匹配） */
    private String industryName;

    /** 客群（ENTERPRISE 企业 / PERSONAL 个人） */
    private String customerGroup;

    /** 维度编码（TAX_INTENSITY / INVOICE_SCALE / OPERATE_YEARS / FINANCIAL_HEALTH / MATCH_OVERALL） */
    private String dimensionCode;

    /** 维度名称（与前端展示文案一致） */
    private String dimensionName;

    /** 行业均值（0-100 归一化，诊断多维统计对比基线） */
    private Integer avgScore;

    /** 25 分位（预留分层展示） */
    private Integer p25Score;

    /** 75 分位（预留分层展示） */
    private Integer p75Score;

    /** 样本量（数据可信度提示） */
    private Integer sampleSize;

    /** 统计周期（如 2026H1 / 2025Y，同维度可多版本并存） */
    private String statPeriod;

    /** 数据来源（MANUAL 人工维护 / INTERNAL 内部样本统计 / EXTERNAL 第三方） */
    private String dataSource;

    /** 状态（ACTIVE 生效 / DISABLED 停用） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
