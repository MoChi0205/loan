package com.loan.serviceops.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceOperationDtoBoundaryTest {

    @Test
    void customerDtoDoesNotExposeInternalOrBankMatchingFields() {
        Set<String> names = Arrays.stream(CustomerAppointmentDTO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(names.contains("adviserName"));
        assertTrue(names.contains("locationName"));
        assertTrue(names.contains("status"));
        assertTrue(names.contains("nextAction"));
        assertFalse(names.contains("internalNote"));
        assertFalse(names.contains("hostStaffCode"));
        assertFalse(names.contains("bankProductCode"));
        assertFalse(names.contains("matchedProducts"));
        assertFalse(names.contains("admissionRules"));
    }

    @Test
    void channelDtoCarriesNoListOrCustomerFields() {
        Set<String> names = Arrays.stream(ChannelServiceAccessDTO.class.getDeclaredFields())
                .map(Field::getName)
                .filter(name -> !"serialVersionUID".equals(name))
                .collect(Collectors.toSet());
        assertTrue(names.contains("available"));
        assertTrue(names.contains("message"));
        assertFalse(names.contains("appointments"));
        assertFalse(names.contains("clientCode"));
    }
}
