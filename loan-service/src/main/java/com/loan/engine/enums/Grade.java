package com.loan.engine.enums;

import lombok.Getter;

/**
 * 匹配档位（第 10 章定稿：档位制，不用百分比）。
 *
 * <p>客户端主展示「匹配程度：高/中/低」，百分比属伪精确（银行审批含人工与政策变量，
 * 系统算不出真实通过率），避免被截图当承诺。档位映射规则随报告模板版本配置。
 *
 * @author loan-platform
 */
@Getter
public enum Grade {

    /** 高（命中核心维度全过，预计可进件数较多） */
    HIGH("HIGH", "高"),

    /** 中 */
    MIDDLE("MIDDLE", "中"),

    /** 低 */
    LOW("LOW", "低");

    /** 档位编码 */
    private final String code;

    /** 档位名称 */
    private final String name;

    Grade(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按编码解析（未命中返回 null）。
     *
     * @param code 档位编码
     * @return 档位枚举，未命中为 null
     */
    public static Grade fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (Grade grade : values()) {
            if (grade.code.equals(code)) {
                return grade;
            }
        }
        return null;
    }
}
