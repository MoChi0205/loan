package com.loan.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前登录用户注解（第二十二轮定稿）。
 *
 * <p>标注在 Controller 方法参数上，由 {@code CurrentUserAspect} 切面从 {@link UserContext}
 * 注入当前 {@link LoanUser} 对象。示例：
 * <pre>
 * {@code @GetMapping("/me")
 * public Result<LoanUser> me(@CurrentUser LoanUser user) { ... }}
 * </pre>
 * 指定字段名可只取某字段值：
 * <pre>
 * {@code public Result<String> no(@CurrentUser("userNo") String userNo) { ... }}
 * </pre>
 *
 * @author loan-platform
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /**
     * 需要注入的字段名；为空时注入完整 {@link LoanUser} 对象。
     * 可选字段：userId / userNo / phone / name / userType / referrerNo / referrerName / region / avatar / roleCode / deptId。
     *
     * @return 字段名，空表示完整对象
     */
    String value() default "";
}
