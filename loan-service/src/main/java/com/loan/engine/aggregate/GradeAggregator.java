package com.loan.engine.aggregate;

import com.loan.engine.enums.Grade;
import org.springframework.stereotype.Component;

/**
 * 档位聚合层（第 10 章定稿：命中产品数 → 高/中/低，不用百分比）。
 *
 * <p>引擎输出不变，档位是展示层聚合；档位映射规则随报告模板版本配置。
 * 阶段一内置默认规则：命中 PASS 产品数 ≥3 为高，≥1 为中，0 为低。
 * 后续可扩展为读取 {@code t_report_template.grade_rule_json} 动态配置。
 *
 * @author loan-platform
 */
@Component
public class GradeAggregator {

    /** 默认「高」档位命中产品数阈值 */
    private static final int DEFAULT_HIGH_THRESHOLD = 3;

    /**
     * 按命中产品数聚合档位（默认阈值）。
     *
     * @param passCount 命中 PASS 的产品数
     * @return 档位
     */
    public Grade aggregate(int passCount) {
        return aggregate(passCount, DEFAULT_HIGH_THRESHOLD);
    }

    /**
     * 按命中产品数聚合档位（自定义阈值）。
     *
     * @param passCount     命中 PASS 的产品数
     * @param highThreshold 「高」档位阈值（命中 PASS 数 ≥ 该值判定为高）
     * @return 档位
     */
    public Grade aggregate(int passCount, int highThreshold) {
        if (passCount >= highThreshold) {
            return Grade.HIGH;
        }
        if (passCount >= 1) {
            return Grade.MIDDLE;
        }
        return Grade.LOW;
    }
}
