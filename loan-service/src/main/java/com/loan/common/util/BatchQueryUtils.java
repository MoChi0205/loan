package com.loan.common.util;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 批量查询参数统一校验：去空、去重、保序并限制单批大小。 */
public final class BatchQueryUtils {

    /** 单次批量查询最大业务编码数。 */
    public static final int MAX_BATCH_SIZE = 100;

    private BatchQueryUtils() {
    }

    /**
     * 规范化业务编码集合。
     *
     * @param codes 原始编码
     * @return 去空、去重且保持请求顺序的编码
     */
    public static List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "业务编码列表不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                normalized.add(code.trim());
            }
        }
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "业务编码列表不能为空");
        }
        if (normalized.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次批量查询最多支持100条");
        }
        return new ArrayList<>(normalized);
    }
}
