package com.loan.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
