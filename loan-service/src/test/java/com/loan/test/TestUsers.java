package com.loan.test;

import com.loan.context.LoanUser;

import java.lang.reflect.Field;

/**
 * 测试用登录态构造器：生成"真实"的 LoanUser（非 mock），避免以下问题：
 * <ul>
 *   <li>端点回显 @CurrentUser 对象时 Jackson 序列化 mock 内部字段导致 500；</li>
 *   <li>mock getter 返回 null 触发控制器"请先登录 / 仅员工可操作"等误拦。</li>
 * </ul>
 * 通过反射填充关键字段（userNo / userType=STAFF / roleCode=STAFF 等），兼容 Lombok @Data 的 setter。
 */
public class TestUsers {

    public static LoanUser staffUser() {
        try {
            LoanUser u = LoanUser.class.getDeclaredConstructor().newInstance();
            set(u, "userId", 1L);
            set(u, "userNo", "T001");
            set(u, "phone", "13800000000");
            set(u, "name", "tester");
            set(u, "userType", "STAFF");
            set(u, "roleCode", "STAFF");
            set(u, "region", "cn");
            set(u, "referrerNo", "R001");
            set(u, "referrerName", "referrer");
            set(u, "avatar", "");
            set(u, "deptCode", "D001");
            set(u, "invitedFlag", false);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    private static void set(Object o, String field, Object val) {
        try {
            Field f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(o, val);
        } catch (Exception ignored) {
            // 字段不存在则跳过
        }
    }
}
