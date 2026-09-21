package com.loan.serviceops.dto;

import com.loan.api.dto.PageResult;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/** P1 直接读业务表的今日服务台；P4 再切换为预聚合缓存。 */
@Data
public class DailyServiceWorkbenchDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private PageResult<StaffAppointmentDTO> companyVisits;
    private PageResult<StaffOutingDTO> staffOutings;
    private PageResult<DailyFollowDTO> pendingFollows;
    private PageResult<DailyOrderDTO> activeOrders;
}
