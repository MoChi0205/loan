package com.loan.engine.execute;

import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.enums.StepResult;
import org.springframework.stereotype.Component;

/**
 * 客群分流器（第 4 章定稿：身份选择后锁定，严格硬分流）。
 *
 * <p>判断客户客群与规则/产品客群是否一致：一致放行，串客群返回 {@link StepResult#SKIP_SEGMENT_MISMATCH}。
 *
 * @author loan-platform
 */
@Component
public class SegmentRouter {

    /**
     * 判断客户客群是否匹配目标客群（COMMON 视为通用，恒放行）。
     *
     * @param clientGroup 客户客群
     * @param targetGroup 目标客群（可为 COMMON 通用）
     * @return true 匹配（放行）
     */
    public boolean matches(CustomerGroup clientGroup, CustomerGroup targetGroup) {
        if (clientGroup == null || targetGroup == null) {
            return false;
        }
        return clientGroup == targetGroup;
    }

    /**
     * 判断客群编码是否匹配（兼容 COMMON 通用客群）。
     *
     * @param clientGroup 客户客群
     * @param targetCode  目标客群编码（PERSONAL/ENTERPRISE/COMMON）
     * @return true 匹配
     */
    public boolean matches(CustomerGroup clientGroup, String targetCode) {
        if ("COMMON".equalsIgnoreCase(targetCode)) {
            return true;
        }
        CustomerGroup target = CustomerGroup.fromCode(targetCode);
        return target != null && matches(clientGroup, target);
    }
}
