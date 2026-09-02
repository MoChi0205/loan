package com.loan.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户档案实体（t_client_profile，认证通过后由线索转正生成）。
 *
 * <p>身份与关联一律业务编码：client_code / owner_staff_code / lead_no，不暴露自增主键。
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_profile")
public class ClientProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（业务唯一ID：client + 32 位随机） */
    private String clientCode;

    /** 客群（ENTERPRISE / PERSONAL） */
    private String customerGroup;

    /** 联系人 */
    private String contactName;

    /** 手机号（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 哈希（查重与等值查询） */
    private String phoneHash;

    /** 企业名称（企业客群） */
    private String enterpriseName;

    /** 归属顾问工号（业务编码） */
    private String ownerStaffCode;

    /** 最后跟进时间（超期回收判定基准；归属/转移/跟进时刷新） */
    private LocalDateTime lastFollowedAt;

    /** 回收冷却到期时间（回收后进公海，冷却期内原归属人不可认领/不可被直接分配） */
    private LocalDateTime assignBlockedUntil;

    /** 来源（线索转正 / 渠道等） */
    private String source;

    /** 状态（ACTIVE 有效 / 等） */
    private String status;

    /** 备注 */
    private String remark;

    /** 受邀标记 */
    private Integer invitedFlag;

    /** 已添加企微 */
    private Integer wecomAdded;

    /** VIP 等级 */
    private String vipLevel;

    /** VIP 到期时间 */
    private LocalDateTime vipExpireAt;

    /** 统一社会信用代码（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String creditCode;

    /** 信用代码 SHA-256 哈希 */
    private String creditCodeHash;

    /** 微信小程序 openid（Q3 方案 A 客户账号主键，UNIQUE，落库明文） */
    private String wxOpenid;

    /** openid SHA-256 哈希（等值查询，防 openid 直存检索） */
    private String wxOpenidHash;

    /** 来源线索编号（业务唯一ID：lead + 32 位随机） */
    private String leadNo;

    /** 扩展预留 JSON */
    private String extJson;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
