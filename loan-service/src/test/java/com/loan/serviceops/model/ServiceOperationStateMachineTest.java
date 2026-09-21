package com.loan.serviceops.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceOperationStateMachineTest {

    @Test
    void onSiteMustArriveBeforeServing() {
        assertTrue(AppointmentStateMachine.canTransition(
                ServiceMethod.COMPANY_ON_SITE, AppointmentStatus.CONFIRMED, AppointmentStatus.ARRIVED));
        assertFalse(AppointmentStateMachine.canTransition(
                ServiceMethod.COMPANY_ON_SITE, AppointmentStatus.CONFIRMED, AppointmentStatus.SERVING));
        assertTrue(AppointmentStateMachine.canTransition(
                ServiceMethod.COMPANY_ON_SITE, AppointmentStatus.ARRIVED, AppointmentStatus.SERVING));
    }

    @Test
    void remoteAndHomeVisitNeverUseArrived() {
        for (ServiceMethod method : new ServiceMethod[]{
                ServiceMethod.HOME_VISIT, ServiceMethod.VIDEO_MEETING, ServiceMethod.PHONE_CONSULT}) {
            assertFalse(AppointmentStateMachine.canTransition(
                    method, AppointmentStatus.CONFIRMED, AppointmentStatus.ARRIVED));
            assertTrue(AppointmentStateMachine.canTransition(
                    method, AppointmentStatus.CONFIRMED, AppointmentStatus.SERVING));
        }
    }

    @Test
    void selfChangeAllowsExactlyFiveMinutesButNotInsideCutoff() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 21, 10, 0);
        assertTrue(AppointmentStateMachine.canSelfChange(start, start.minusMinutes(5)));
        assertFalse(AppointmentStateMachine.canSelfChange(start, start.minusMinutes(5).plusSeconds(1)));
        assertFalse(AppointmentStateMachine.canSelfChange(start, start));
    }

    @Test
    void outingRequiresApprovalBeforeDepartureAndReturnAfterDeparture() {
        // 申请 → 审核
        assertTrue(OutingStateMachine.canTransition(OutingStatus.DRAFT, OutingStatus.PENDING_REVIEW));
        assertTrue(OutingStateMachine.canTransition(OutingStatus.PENDING_REVIEW, OutingStatus.READY));
        assertTrue(OutingStateMachine.canTransition(OutingStatus.PENDING_REVIEW, OutingStatus.REJECTED));
        // 驳回后可修改重提
        assertTrue(OutingStateMachine.canTransition(OutingStatus.REJECTED, OutingStatus.PENDING_REVIEW));
        // 未审核通过（待审 / 已驳回）不得打卡
        assertFalse(OutingStateMachine.canTransition(OutingStatus.PENDING_REVIEW, OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canTransition(OutingStatus.REJECTED, OutingStatus.IN_PROGRESS));
        // 必须先出发再返回
        assertTrue(OutingStateMachine.canTransition(OutingStatus.READY, OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canTransition(OutingStatus.READY, OutingStatus.COMPLETED));
        assertTrue(OutingStateMachine.canTransition(OutingStatus.IN_PROGRESS, OutingStatus.COMPLETED));
        // 终态不可再流转
        assertFalse(OutingStateMachine.canTransition(OutingStatus.COMPLETED, OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canTransition(OutingStatus.CANCELLED, OutingStatus.READY));
    }

    @Test
    void outingReviewAndCheckInGuards() {
        assertTrue(OutingStateMachine.canReview(OutingStatus.PENDING_REVIEW));
        assertFalse(OutingStateMachine.canReview(OutingStatus.REJECTED));
        assertFalse(OutingStateMachine.canReview(OutingStatus.READY));
        assertFalse(OutingStateMachine.canReview(OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canReview(OutingStatus.COMPLETED));
        assertTrue(OutingStateMachine.canDepart(OutingStatus.READY));
        assertFalse(OutingStateMachine.canDepart(OutingStatus.PENDING_REVIEW));
        assertTrue(OutingStateMachine.canReturn(OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canReturn(OutingStatus.READY));
        assertTrue(OutingStatus.COMPLETED.isTerminal());
        assertTrue(OutingStatus.CANCELLED.isTerminal());
        assertFalse(OutingStatus.REJECTED.isTerminal());
    }

    @Test
    void onlyConfirmedAppointmentCanBecomeNoShow() {
        assertTrue(AppointmentStateMachine.canTransition(
                ServiceMethod.COMPANY_ON_SITE, AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW));
        assertFalse(AppointmentStateMachine.canTransition(
                ServiceMethod.COMPANY_ON_SITE, AppointmentStatus.ARRIVED, AppointmentStatus.NO_SHOW));
        assertFalse(AppointmentStateMachine.canTransition(
                ServiceMethod.PHONE_CONSULT, AppointmentStatus.SERVING, AppointmentStatus.NO_SHOW));
    }
}
