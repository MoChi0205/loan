package com.loan.sensitive.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 敏感数据查看额度 VO（日限额 / 已用 / 剩余）。
 *
 * @author loan-platform
 */
@Data
public class SensitiveQuotaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当日上限 */
    private int limit;

    /** 已用 */
    private int used;

    /** 剩余 */
    private int remaining;
}
