package com.loan.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结果（对齐 tse PageResult）。
 *
 * <p>分页参数：page 从 1 起、size 默认 10 最大 100。
 *
 * @param <T> 记录泛型
 * @author loan-platform
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码（从 1 起） */
    private long page;

    /** 每页大小 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 当前页记录列表 */
    private List<T> records;

    /**
     * 构建空分页结果。
     *
     * @param page 当前页码
     * @param size 每页大小
     * @param <T>  记录泛型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(long page, long size) {
        return build(page, size, 0, Collections.<T>emptyList());
    }

    /**
     * 构建分页结果。
     *
     * @param page    当前页码
     * @param size    每页大小
     * @param total   总记录数
     * @param records 记录列表
     * @param <T>     记录泛型
     * @return 分页结果
     */
    public static <T> PageResult<T> build(long page, long size, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setRecords(records == null ? Collections.<T>emptyList() : records);
        return result;
    }
}
