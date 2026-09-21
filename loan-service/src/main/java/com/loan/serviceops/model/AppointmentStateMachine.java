package com.loan.serviceops.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** P0 预约状态机与“开始前 5 分钟”变更规则。 */
public final class AppointmentStateMachine {

    public static final Duration SELF_SERVICE_CHANGE_CUTOFF = Duration.ofMinutes(5);

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> COMMON_TRANSITIONS;

    static {
        Map<AppointmentStatus, Set<AppointmentStatus>> transitions =
                new EnumMap<>(AppointmentStatus.class);
        transitions.put(AppointmentStatus.REQUESTED, EnumSet.of(
                AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED));
        transitions.put(AppointmentStatus.CONFIRMED, EnumSet.of(
                AppointmentStatus.SERVING, AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW, AppointmentStatus.RESCHEDULED));
        transitions.put(AppointmentStatus.ARRIVED, EnumSet.of(
                AppointmentStatus.SERVING, AppointmentStatus.CANCELLED));
        transitions.put(AppointmentStatus.SERVING, EnumSet.of(
                AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED));
        COMMON_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private AppointmentStateMachine() {
    }

    public static boolean canTransition(ServiceMethod method,
                                        AppointmentStatus current,
                                        AppointmentStatus target) {
        if (method == null || current == null || target == null || current.isTerminal()) {
            return false;
        }
        if (method.countsAsOnSiteVisit()
                && current == AppointmentStatus.CONFIRMED
                && target == AppointmentStatus.ARRIVED) {
            return true;
        }
        // 非现场服务绝不能产生 ARRIVED；现场服务必须先到店再开始服务。
        if (target == AppointmentStatus.ARRIVED
                || (method.countsAsOnSiteVisit()
                    && current == AppointmentStatus.CONFIRMED
                    && target == AppointmentStatus.SERVING)) {
            return false;
        }
        return COMMON_TRANSITIONS.getOrDefault(current, Collections.emptySet()).contains(target);
    }

    /** 恰好在截止时间点仍允许；进入最后 5 分钟后转员工异常处理。 */
    public static boolean canSelfChange(LocalDateTime scheduledStart, LocalDateTime now) {
        if (scheduledStart == null || now == null) {
            return false;
        }
        return !now.isAfter(scheduledStart.minus(SELF_SERVICE_CHANGE_CUTOFF));
    }
}
