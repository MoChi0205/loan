package com.loan.mini.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 员工侧内部匹配结果；禁止用于客户响应。 */
@Data
public class MiniMatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告编号（业务唯一ID：report + 32 位随机） */
    private String reportNo;

    /** 档位编码（HIGH/MIDDLE/LOW） */
    private String grade;

    /** 三档总结果（PASS=可进件 / CONDITION=需补料 / REJECT=暂不匹配） */
    private String totalResult;

    /** 命中产品数量（内部算出来只回传数量，不回传明细） */
    private Integer productCount;

    /** 用户评级（按档位映射：高/中/低） */
    private String rating;

    /** 逐条规则说明（脱敏，不含产品明细） */
    private List<RuleHit> ruleLogs;
}
