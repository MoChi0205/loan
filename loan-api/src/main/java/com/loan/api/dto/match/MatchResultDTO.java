package com.loan.api.dto.match;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 匹配结果总览（档位 + 数量 + 产品结果树，Dubbo 跨系统契约）。
 *
 * <p>客户端仅见档位与数量，产品明细仅管理端/内部系统可见。
 *
 * @author loan-platform
 */
@Data
public class MatchResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 匹配档位（HIGH/MIDDLE/LOW，不用百分比） */
    private String grade;

    /** 预计可进件银行数 */
    private int bankCount;

    /** 命中产品总数 */
    private int productCount;

    /** 可进件（PASS）产品数 */
    private int passCount;

    /** 需补料（CONDITION）产品数 */
    private int conditionCount;

    /** 暂不匹配（REJECT）产品数 */
    private int rejectCount;

    /** 产品匹配结果树 */
    private List<ProductMatchDTO> products = new ArrayList<>();
}
