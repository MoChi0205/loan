package com.loan.common.util;

/**
 * 分页参数统一归一化工具。
 *
 * <p>所有 HTTP / Dubbo 分页入口均使用从 1 开始的页码，每页最大 100 条；
 * 该工具只负责边界校验，不承载业务查询条件，便于各资源复用同一契约。</p>
 */
public final class PageParams {

    /** 默认页码。 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页大小。 */
    public static final int DEFAULT_SIZE = 10;
    /** 最大每页大小，与 MyBatis-Plus 分页插件保持一致。 */
    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /** 将页码归一化到大于等于 1。 */
    public static int page(int page) {
        return page <= 0 ? DEFAULT_PAGE : page;
    }

    /** 将每页大小归一化到 1～100。 */
    public static int size(int size) {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
