package com.loan.engine.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品匹配结果（结果树根节点，含模块列表）。
 *
 * @author loan-platform
 */
@Data
public class ProductMatchVO {

    /** 产品 ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称（仅管理端可见） */
    private String productName;

    /** 产品匹配总结果（PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR） */
    private String totalResult;

    /** 模块结果列表 */
    private List<ModuleMatchVO> modules = new ArrayList<>();
}
