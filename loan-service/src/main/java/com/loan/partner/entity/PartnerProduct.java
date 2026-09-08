package com.loan.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合作库上架实体（t_partner_product，对客可见权在我司）。
 *
 * <p>bankProductCode 为银行产品业务编码（评审决策替代原 bigint bank_product_id）；
 * 状态机：ACTIVE 上架 → EXPIRING 临期（T-30/T-7 预警）→ EXPIRED 到期 / OFFLINE 手动下架；
 * 续签（renew）更新 cooperateUntil 并回到 ACTIVE。
 *
 * @author loan-platform
 */
@Data
@TableName("t_partner_product")
public class PartnerProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 银行产品内部物理主键（兼容旧库 t_partner_product.bank_product_id NOT NULL）。
     *
     * <p>评审决策后 t_partner_product 计划改为仅保留 bank_product_code 业务唯一键，
     * 但在迁移 mvp-bizid-fk 落地前，旧库列仍然 NOT NULL 且无默认值——INSERT 时必须回填
     * {@code t_bank_product.id}，否则触发 "Field 'bank_product_id' doesn't have a default value"
     * 系统异常（D67）。迁移完成后本字段标记 {@code @TableField(exist = false)} 即可下线。</p>
     */
    private Long bankProductId;

    /** 银行产品业务编码（小写前缀+32位随机，业务唯一） */
    private String bankProductCode;

    /** 合作库有效期（到期自动下架） */
    private LocalDateTime cooperateUntil;

    /** 状态（ACTIVE 上架 / EXPIRING 临期 / EXPIRED 到期 / OFFLINE 手动下架） */
    private String status;

    /** 创建人姓名 */
    private String createdBy;

    /** 更新人姓名 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 产品名称（批量装配的展示字段，不落合作库表） */
    @TableField(exist = false)
    private String productName;
}
