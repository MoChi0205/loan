package com.loan.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户统一对象（认证信息里携带，按用户类型填充，可空字段置 null）。
 *
 * <p>通过 {@code @CurrentUser} 注解 + AOP 切面注入，或 {@link UserContext#getUser()} 静态获取。
 *
 * @author loan-platform
 */
@Data
public class LoanUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户类型：CUSTOMER 客户 / CHANNEL 渠道 / STAFF 员工 */
    public static final String TYPE_CUSTOMER = "CUSTOMER";
    public static final String TYPE_CHANNEL = "CHANNEL";
    public static final String TYPE_STAFF = "STAFF";

    /** 内部 ID（客户档案 ID / 渠道账号 ID / 员工 ID） */
    private Long userId;

    /** 用户编号（客户编号 client_code / 员工工号 staff_code / 渠道账号） */
    private String userNo;

    /** 手机号（内存明文，出参按需脱敏） */
    private String phone;

    /** 姓名 */
    private String name;

    /** 用户类型（CUSTOMER / CHANNEL / STAFF） */
    private String userType;

    /** 所属邀请人编号（仅客户，取自 t_invitation 绑定关系） */
    private String referrerNo;

    /** 所属邀请人姓名（仅客户） */
    private String referrerName;

    /** 地区（客户档案城市：个人 city / 企业注册地） */
    private String region;

    /** 头像 URL */
    private String avatar;

    /** 角色（仅员工：BOSS / DEPT_MANAGER / ADVISER） */
    private String roleCode;

    /** 部门编码（仅员工，业务编码） */
    private String deptCode;

    /** 所属银行渠道 ID（仅渠道，t_channel_user.bank_channel_id，数据范围硬隔离用；T11/D28） */
    private Long bankChannelId;

    /** 所属银行渠道编码（仅渠道，t_bank_channel.channel_code 业务编码；T11/D28） */
    private String bankChannelCode;

    /** 受邀标记（仅客户：受邀用户免费自动 VIP + 独享分享推荐奖励） */
    private Boolean invitedFlag;
}
