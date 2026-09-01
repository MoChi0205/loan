package com.loan.api.dto.match;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品匹配结果（结果树根节点，Dubbo 跨系统契约）。
 *
 * @author loan-platform
 */
@Data
public class ProductMatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品 ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称（仅管理端可见） */
    private String productName;

    /** 产品匹配总结果（PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR） */
    private String totalResult;

    /** 模块结果列表 */
    private List<ModuleMatchDTO> modules = new ArrayList<>();
}
