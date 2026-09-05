package com.loan.channel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道银行账号实体（t_channel_user，密码 BCrypt，手机号 AES 加密）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_channel_user")
public class ChannelUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属银行渠道 ID（数据范围硬隔离） */
    private Long bankChannelId;

    /** 手机号（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 哈希（登录与查重） */
    private String phoneHash;

    /** 密码（BCrypt） */
    private String password;

    /** 姓名 */
    private String name;

    /** 岗位 */
    private String jobTitle;

    /** 注册方式（INVITE_CODE 邀请码自助注册） */
    private String registerType;

    /** 状态（ACTIVE/DISABLED，停用即时踢下线） */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
