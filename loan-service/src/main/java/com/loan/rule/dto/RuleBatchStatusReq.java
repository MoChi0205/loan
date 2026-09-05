package com.loan.rule.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 规则批量更新状态请求（管理端）。
 *
 * @author loan-platform
 */
@Data
public class RuleBatchStatusReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码列表（业务唯一ID） */
    private List<String> ruleCodes;

    /** 目标状态（ONLINE/DISABLED） */
    private String status;
}
