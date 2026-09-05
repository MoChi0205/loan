package com.loan.infrastructure.aspect;

import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.common.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 当前用户注解切面（第二十二轮定稿，参考 tse AuthenticationGuardAspect / MenuPermissionAspect）。
 *
 * <p>拦截 Controller 方法，将标注 {@code @CurrentUser} 的参数从 {@link UserContext} 注入。
 * 未登录时注入 null（由后续鉴权逻辑决定是否拒绝）。
 *
 * @author loan-platform
 */
@Aspect
@Component
@Order(1)
public class CurrentUserAspect {

    /**
     * 环绕通知：注入当前用户参数。
     *
     * @param joinPoint 连接点
     * @return 目标方法返回值
     * @throws Throwable 目标方法异常
     */
    @Around("execution(* com.loan..controller..*(..))")
    public Object injectCurrentUser(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        boolean injected = false;
        for (int i = 0; i < parameterAnnotations.length; i++) {
            CurrentUser currentUser = findCurrentUser(parameterAnnotations[i]);
            if (currentUser == null) {
                continue;
            }
            args[i] = resolveArgument(currentUser);
            injected = true;
        }
        // 无 @CurrentUser 参数直接放行，避免无效切面开销
        if (!injected) {
            return joinPoint.proceed(args);
        }
        return joinPoint.proceed(args);
    }

    /**
     * 查找参数上的 {@code @CurrentUser} 注解。
     *
     * @param annotations 参数注解数组
     * @return 注解实例，不存在返回 null
     */
    private CurrentUser findCurrentUser(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof CurrentUser) {
                return (CurrentUser) annotation;
            }
        }
        return null;
    }

    /**
     * 解析参数值：value 为空注入完整 LoanUser，否则按字段名取对应字段值。
     *
     * @param currentUser 注解实例
     * @return 参数值
     */
    private Object resolveArgument(CurrentUser currentUser) {
        LoanUser user = UserContext.getUser();
        String field = currentUser.value();
        if (field == null || field.isEmpty()) {
            return user;
        }
        if (user == null) {
            return null;
        }
        switch (field) {
            case "userId":
                return user.getUserId();
            case "userNo":
                return user.getUserNo();
            case "phone":
                return user.getPhone();
            case "name":
                return user.getName();
            case "userType":
                return user.getUserType();
            case "referrerNo":
                return user.getReferrerNo();
            case "referrerName":
                return user.getReferrerName();
            case "region":
                return user.getRegion();
            case "avatar":
                return user.getAvatar();
            case "roleCode":
                return user.getRoleCode();
            case "deptCode":
                return user.getDeptCode();
            case "invitedFlag":
                return user.getInvitedFlag();
            default:
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "未知的 @CurrentUser 字段: " + field);
        }
    }
}
