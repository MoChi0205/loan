package com.loan.client.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端客户档案编辑请求（P0-6，基础信息 + 个人档案字段合并更新）。
 *
 * <p>敏感字段明文传输：phone / idCardNo / creditCode 服务端写入时 AES 加密 + SHA-256 哈希落库；
 * 合并更新语义：仅提交的非空字段生效，未提交字段保持原值。
 *
 * @author loan-platform
 */
@Data
public class ClientUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 联系人（基础信息） */
    private String contactName;

    /** 手机号（明文，服务端 AES 加密 + hash 落库） */
    private String phone;

    /** 备注 */
    private String remark;

    /** 企业名称（企业信息） */
    private String enterpriseName;

    /** 统一社会信用代码（明文，服务端 AES 加密 + hash 落库） */
    private String creditCode;

    /** 个人档案-姓名 */
    private String realName;

    /** 个人档案-身份证号（明文，服务端 AES 加密 + hash 落库） */
    private String idCardNo;

    /** 个人档案-城市 */
    private String city;

    /** 个人档案-年龄 */
    private Integer age;

    /** 个人档案-房产（0 无 / 1 有） */
    private Integer houseFlag;

    /** 个人档案-车辆（0 无 / 1 有） */
    private Integer carFlag;

    /** 个人档案-社保（0 无 / 1 有） */
    private Integer socialSecurityFlag;

    /** 个人档案-公积金（0 无 / 1 有） */
    private Integer fundFlag;
}
