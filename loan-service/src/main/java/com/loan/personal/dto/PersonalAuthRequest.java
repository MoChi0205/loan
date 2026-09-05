package com.loan.personal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序个人认证请求。
 *
 * @author loan-platform
 */
@Data
public class PersonalAuthRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 姓名 */
    private String realName;

    /** 身份证号（明文传输，服务端 AES 加密 + SHA-256 哈希落库） */
    private String idCardNo;

    /** 城市 */
    private String city;

    /** 年龄 */
    private Integer age;

    /** 房产（0 无 / 1 有） */
    private Integer houseFlag;

    /** 车辆（0 无 / 1 有） */
    private Integer carFlag;

    /** 社保（0 无 / 1 有） */
    private Integer socialSecurityFlag;

    /** 公积金（0 无 / 1 有） */
    private Integer fundFlag;
}
