package com.loan.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结果（Dubbo 跨系统契约）。
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
        PageResult<T> r = new PageResult<>();
        r.setPage(page);
        r.setSize(size);
        r.setTotal(total);
        r.setRecords(records == null ? Collections.<T>emptyList() : records);
        return r;
    }
}
