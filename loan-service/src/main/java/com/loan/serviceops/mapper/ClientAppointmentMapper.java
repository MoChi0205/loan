package com.loan.serviceops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.serviceops.entity.ClientAppointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ClientAppointmentMapper extends BaseMapper<ClientAppointment> {

    @Update("UPDATE t_client_appointment SET status=#{target}, actual_arrived_at=COALESCE(#{arrivedAt},actual_arrived_at), "
            + "actual_left_at=COALESCE(#{leftAt},actual_left_at), cancel_reason=COALESCE(#{reason},cancel_reason), "
            + "updated_by=#{operator}, updated_at=#{now} WHERE appointment_no=#{appointmentNo} AND status=#{expected}")
    int transition(@Param("appointmentNo") String appointmentNo,
                   @Param("expected") String expected,
                   @Param("target") String target,
                   @Param("arrivedAt") LocalDateTime arrivedAt,
                   @Param("leftAt") LocalDateTime leftAt,
                   @Param("reason") String reason,
                   @Param("operator") String operator,
                   @Param("now") LocalDateTime now);
}
