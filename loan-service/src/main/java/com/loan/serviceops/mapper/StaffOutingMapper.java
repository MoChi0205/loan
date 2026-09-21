package com.loan.serviceops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.serviceops.entity.StaffOuting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface StaffOutingMapper extends BaseMapper<StaffOuting> {

    @Update("UPDATE t_staff_outing SET status='IN_PROGRESS', actual_departed_at=#{at}, "
            + "departed_location_ciphertext=#{locationCiphertext}, updated_by=#{operator}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='READY' AND actual_departed_at IS NULL")
    int depart(@Param("outingNo") String outingNo,
               @Param("at") LocalDateTime at,
               @Param("locationCiphertext") String locationCiphertext,
               @Param("operator") String operator);

    @Update("UPDATE t_staff_outing SET status='COMPLETED', actual_returned_at=#{at}, "
            + "returned_location_ciphertext=#{locationCiphertext}, updated_by=#{operator}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='IN_PROGRESS' "
            + "AND actual_departed_at IS NOT NULL AND actual_returned_at IS NULL")
    int returnFromOuting(@Param("outingNo") String outingNo,
                         @Param("at") LocalDateTime at,
                         @Param("locationCiphertext") String locationCiphertext,
                         @Param("operator") String operator);
}
