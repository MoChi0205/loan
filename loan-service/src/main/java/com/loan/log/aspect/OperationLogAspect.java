package com.loan.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.log.annotation.OpLog;
import com.loan.log.entity.OperationLog;
import com.loan.log.mapper.OperationLogMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 操作日志切面（参考 tse ApiAccessLogAspect）。
 *
 * <p>拦截标注 {@link OpLog} 的写操作，记录 bizType/action/operator/ip/参数快照到 t_operation_log。
 * 参数快照自动脱敏（手机号/身份证等），日志失败不影响主流程。
 *
 * @author loan-platform
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 拦截 @OpLog 注解方法。
     *
     * @param joinPoint 切点
     * @param opLog     注解
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            try {
                record(joinPoint, opLog);
            } catch (Exception e) {
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 落库操作日志。
     */
    private void record(ProceedingJoinPoint joinPoint, OpLog annotation) {
        OperationLog entity = new OperationLog();
        entity.setBizType(annotation.bizType());
        entity.setAction(annotation.action());

        LoanUser user = UserContext.getUser();
        entity.setOperator(user == null ? "system" : user.getName());
        entity.setOperatorRole(user == null ? null : user.getRoleCode());
        entity.setIp(currentIp());
        entity.setCreatedAt(LocalDateTime.now());

        // 参数快照（脱敏）
        try {
            Object[] args = joinPoint.getArgs();
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof HttpServletRequest) {
                    continue;
                }
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"arg").append(i).append("\":");
                sb.append(maskSensitive(objectMapper.writeValueAsString(arg)));
            }
            sb.append("}");
            entity.setDetailJson(sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString());
        } catch (Exception e) {
            entity.setDetailJson("{}");
        }
        operationLogMapper.insert(entity);
    }

    /**
     * 参数快照脱敏（手机号/身份证替换为掩码）。
     */
    private String maskSensitive(String json) {
        if (json == null) {
            return "null";
        }
        // 手机号 1[3-9]xxxxxxxxx → 138****5678
        json = json.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        // 身份证 18 位 → 前6后4
        json = json.replaceAll("(\\d{6})\\d{8}(\\d{3}[0-9Xx])", "$1********$2");
        return json;
    }

    /**
     * 获取当前请求 IP。
     */
    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
