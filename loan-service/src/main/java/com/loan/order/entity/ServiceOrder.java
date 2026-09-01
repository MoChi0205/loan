package com.loan.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务工单实体（t_service_order，业务订单主表，支撑客户 / 推荐人 / 公司三视角）。
 *
 * <p>状态机：NEW 新建 → IN_SERVICE 服务中 → DEAL 已成交 / CANCEL 已取消；
 * DEAL → REFUND 已退款（自动冲减营收与奖励）。
 *
 * <p>身份与关联一律业务编码：order_no / client_profile_code / owner_staff_code / bank_product_code，
 * 不暴露自增主键。
 *
 * @author loan-platform
 */
@Data
@TableName("t_service_order")
public class ServiceOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态机 */
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_IN_SERVICE = "IN_SERVICE";
    public static final String STATUS_DEAL = "DEAL";
    public static final String STATUS_CANCEL = "CANCEL";
    public static final String STATUS_REFUND = "REFUND";

    /** 来源 */
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_OFFLINE_SUPPLEMENT = "OFFLINE_SUPPLEMENT";
    public static final String SOURCE_CRM_WRITEBACK = "CRM_WRITEBACK";

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号（业务唯一ID：order + 32 位随机） */
    private String orderNo;

    /** 客户编码（业务唯一ID：client + 32 位随机） */
    private String clientProfileCode;

    /** 客群（ENTERPRISE 企业 / PERSONAL 个人） */
    private String customerGroup;

    /** 关联银行产品编码（业务编码，可选） */
    private String bankProductCode;

    /** 顾问员工工号（业务编码，谁建单归谁） */
    private String ownerStaffCode;

    /** 成交金额（DEAL 计入营收并触发奖励结算基数） */
    private BigDecimal dealAmount;

    /** 成交时间 */
    private LocalDateTime dealTime;

    /** 客户可见备注（小程序我的服务单展示） */
    private String customerRemark;

    /** 内部备注（仅管理端可见） */
    private String internalRemark;

    /** 支付方式 */
    private String payType;

    /** 凭证附件 ID（线下补录必填，内部引用附件主键） */
    private Long voucherAttachmentId;

    /** 状态（NEW/IN_SERVICE/DEAL/CANCEL/REFUND） */
    private String status;

    /** 来源（CRM_WRITEBACK 合同回写 / OFFLINE_SUPPLEMENT 线下补录 / MANUAL 手工建单） */
    private String source;

    /** 来源单号（CRM 合同号 / 线下成交单号） */
    private String sourceOrderNo;

    /** 奖励结算标记（DEAL 触发结算，REFUND 联动奖励单作废 / 冲正） */
    private Integer rewardSettledFlag;

    /** 创建人姓名（操作人留痕） */
    private String createdBy;

    /** 更新人姓名 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
