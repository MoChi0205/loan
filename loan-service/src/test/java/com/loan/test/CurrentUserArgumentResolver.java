package com.loan.test;

import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 测试用 @CurrentUser 参数解析器：镜像生产 {@code CurrentUserAspect}，从 UserContext(ThreadLocal) 注入。
 * 使 standalone MockMvc 无需启动 Spring 上下文即可模拟登录态，彻底规避 Nacos 远程配置拉取。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        LoanUser user = UserContext.getUser();
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        String field = annotation == null ? null : annotation.value();
        if (field == null || field.isEmpty()) {
            return user;
        }
        if (user == null) {
            return null;
        }
        switch (field) {
            case "userId": return user.getUserId();
            case "userNo": return user.getUserNo();
            case "phone": return user.getPhone();
            case "name": return user.getName();
            case "userType": return user.getUserType();
            case "referrerNo": return user.getReferrerNo();
            case "referrerName": return user.getReferrerName();
            case "region": return user.getRegion();
            case "avatar": return user.getAvatar();
            case "roleCode": return user.getRoleCode();
            case "deptCode": return user.getDeptCode();
            case "invitedFlag": return user.getInvitedFlag();
            default: return null;
        }
    }
}
