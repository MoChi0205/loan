package com.loan.engine.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配结果总览（档位 + 数量 + 产品结果树，客户端仅见档位与数量，产品明细仅管理端）。
 *
 * @author loan-platform
 */
@Data
public class MatchResultVO {

    /** 匹配档位（HIGH/MIDDLE/LOW，不用百分比） */
    private String grade;

    /** 预计可进件银行数（客户端唯一可见数量口径） */
    private int bankCount;

    /** 命中产品总数 */
    private int productCount;

    /** 可进件（PASS）产品数 */
    private int passCount;

    /** 需补料（CONDITION）产品数 */
    private int conditionCount;

    /** 暂不匹配（REJECT）产品数 */
    private int rejectCount;

    /** 产品匹配结果树（仅管理端调试中心可见） */
    private List<ProductMatchVO> products = new ArrayList<>();

    /** 链路追踪 UUID（供审计中心按 traceUuid 查询全链路时间线） */
    private String traceUuid;
}
