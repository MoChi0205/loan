package com.loan.personal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人客户档案实体（t_personal_profile，1:1 扩展）。
 *
 * <p>身份证号 AES 加密落库（{@link AesTypeHandler}），配 id_card_hash 做等值查重；
 * client_profile_code 为客户业务编码关联（评审决策：业务主键一律业务编码）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_personal_profile")
public class PersonalProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（业务ID，1:1） */
    private String clientProfileCode;

    /** 姓名 */
    private String realName;

    /** 身份证号（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String idCardNo;

    /** 身份证号 SHA-256 哈希（查重与等值查询） */
    private String idCardHash;

    /** 年龄 */
    private Integer age;

    /** 城市 */
    private String city;

    /** 房产（0 无 / 1 有） */
    private Integer houseFlag;

    /** 车辆（0 无 / 1 有） */
    private Integer carFlag;

    /** 社保（0 无 / 1 有） */
    private Integer socialSecurityFlag;

    /** 公积金（0 无 / 1 有） */
    private Integer fundFlag;

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
