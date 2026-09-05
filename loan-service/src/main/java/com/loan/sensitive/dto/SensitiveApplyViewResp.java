package com.loan.sensitive.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 敏感数据申请查看响应（授权/豁免后揭示明文手机号）。
 *
 * @author loan-platform
 */
@Data
public class SensitiveApplyViewResp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 线索业务 ID */
    private String leadNo;

    /** 明文手机号（仅授权/豁免角色可见） */
    private String phonePlain;

    /** 脱敏手机号（始终返回，前置展示用） */
    private String phoneMasked;

    /** 是否成功揭示明文 */
    private boolean revealed;

    /** 当日上限 */
    private int limit;

    /** 已用 */
    private int used;

    /** 剩余 */
    private int remaining;
}
