package com.loan.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 分页排序工具：安全地把前端 orderBy/orderDir 映射到实体列（白名单防注入）。
 *
 * <p>用法（各 page 接口）：
 * <pre>
 *   PageOrder.apply(wrapper, orderBy, orderDir,
 *       new java.util.HashMap&lt;&gt;() {{
 *           put("createdAt", Entity::getCreatedAt);
 *           put("updatedAt", Entity::getUpdatedAt);
 *       }},
 *       Entity::getCreatedAt); // 默认排序列
 * </pre>
 * 未传或非法 orderBy 时回退默认列倒序。
 *
 * @author loan-platform
 */
public final class PageOrder {

    private PageOrder() {
    }

    /**
     * 应用排序（白名单映射；orderDir 支持 asc / desc，缺省 desc）。
     *
     * @param wrapper     查询包装器
     * @param orderBy     排序字段（实体属性名，白名单）
     * @param orderDir    排序方向（asc/desc）
     * @param fieldMap    允许排序的字段映射（属性名 → 列）
     * @param defaultField 默认排序列（orderBy 缺省/非法时使用，倒序）
     */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper, String orderBy, String orderDir,
                                 Map<String, SFunction<T, ?>> fieldMap, SFunction<T, ?> defaultField) {
        boolean asc = !"desc".equalsIgnoreCase(orderDir);
        if (StringUtils.hasText(orderBy) && fieldMap != null && fieldMap.containsKey(orderBy)) {
            wrapper.orderBy(true, asc, fieldMap.get(orderBy));
        } else if (defaultField != null) {
            wrapper.orderByDesc(defaultField);
        }
    }
}
