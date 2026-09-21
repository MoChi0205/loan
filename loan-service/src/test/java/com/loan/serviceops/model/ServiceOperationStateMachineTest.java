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
    void outingRequiresDepartureBeforeReturn() {
        assertTrue(OutingStateMachine.canTransition(OutingStatus.DRAFT, OutingStatus.READY));
        assertTrue(OutingStateMachine.canTransition(OutingStatus.READY, OutingStatus.IN_PROGRESS));
        assertFalse(OutingStateMachine.canTransition(OutingStatus.READY, OutingStatus.COMPLETED));
        assertTrue(OutingStateMachine.canTransition(OutingStatus.IN_PROGRESS, OutingStatus.COMPLETED));
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
