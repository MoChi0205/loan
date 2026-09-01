package com.loan.dict.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 枚举字典条目（供前端解析枚举值的中文语义与展示色）。
 *
 * <p>契约约定：前端不得硬编码枚举值。所有枚举的 code（存储/传输值）、
 * label（中文语义展示值）、colorType（语义色，映射前端标签样式）均由后端统一定义下发。
 *
 * @author loan-platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 枚举编码（存储/传输/落库值，如 PASS、ENTERPRISE） */
    private String code;

    /** 中文语义（展示值，如 通过、企业） */
    private String label;

    /** 语义色类型（success/warning/danger/info/primary/muted，前端据此映射标签样式） */
    private String colorType;

    /**
     * 构建字典条目。
     *
     * @param code      枚举编码
     * @param label     中文语义
     * @param colorType 语义色类型
     * @return 字典条目
     */
    public static DictItemVO of(String code, String label, String colorType) {
        return new DictItemVO(code, label, colorType);
    }
}
