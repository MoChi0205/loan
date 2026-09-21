package com.loan.serviceops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.serviceops.entity.StaffOuting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 员工外出 Mapper。
 *
 * <p>所有状态流转都用「条件更新」实现乐观并发控制：WHERE 里同时带上「期望的当前状态」，
 * 更新行数不为 1 即说明状态已被他人改变，由服务层抛出可重试的业务提示。
 */
@Mapper
public interface StaffOutingMapper extends BaseMapper<StaffOuting> {

    /** 审核通过：待审核 → 待出发，写入审核人与时间。 */
    @Update("UPDATE t_staff_outing SET status='READY', reviewer_staff_code=#{reviewerCode}, "
            + "reviewer_name=#{reviewerName}, reviewed_at=#{at}, review_remark=#{remark}, "
            + "updated_by=#{reviewerName}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='PENDING_REVIEW'")
    int approve(@Param("outingNo") String outingNo,
                @Param("reviewerCode") String reviewerCode,
                @Param("reviewerName") String reviewerName,
                @Param("remark") String remark,
                @Param("at") LocalDateTime at);

    /** 审核驳回：待审核 → 已驳回，驳回原因必填由服务层保证。 */
    @Update("UPDATE t_staff_outing SET status='REJECTED', reviewer_staff_code=#{reviewerCode}, "
            + "reviewer_name=#{reviewerName}, reviewed_at=#{at}, review_remark=#{remark}, "
            + "updated_by=#{reviewerName}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='PENDING_REVIEW'")
    int reject(@Param("outingNo") String outingNo,
               @Param("reviewerCode") String reviewerCode,
               @Param("reviewerName") String reviewerName,
               @Param("remark") String remark,
               @Param("at") LocalDateTime at);

    /**
     * 驳回后重新提交：已驳回 → 待审核，同时清空上一轮审核痕迹。
     * 清空是刻意的——避免新一次提交仍挂着旧的驳回意见与审核人，造成误读。
     */
    @Update("UPDATE t_staff_outing SET status='PENDING_REVIEW', submitted_at=#{at}, "
            + "reviewer_staff_code=NULL, reviewer_name=NULL, reviewed_at=NULL, review_remark=NULL, "
            + "updated_by=#{operator}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='REJECTED'")
    int resubmit(@Param("outingNo") String outingNo,
                 @Param("at") LocalDateTime at,
                 @Param("operator") String operator);

    /** 出发打卡：待出发 → 外出中，必须带定位密文与打卡照片。 */
    @Update("UPDATE t_staff_outing SET status='IN_PROGRESS', actual_departed_at=#{at}, "
            + "departed_location_ciphertext=#{locationCiphertext}, departed_photo_key=#{photoKey}, "
            + "updated_by=#{operator}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='READY' AND actual_departed_at IS NULL")
    int depart(@Param("outingNo") String outingNo,
               @Param("at") LocalDateTime at,
               @Param("locationCiphertext") String locationCiphertext,
               @Param("photoKey") String photoKey,
               @Param("operator") String operator);

    /** 返回打卡：外出中 → 已完成，必须带定位密文与打卡照片。 */
    @Update("UPDATE t_staff_outing SET status='COMPLETED', actual_returned_at=#{at}, "
            + "returned_location_ciphertext=#{locationCiphertext}, returned_photo_key=#{photoKey}, "
            + "updated_by=#{operator}, updated_at=#{at} "
            + "WHERE outing_no=#{outingNo} AND status='IN_PROGRESS' "
            + "AND actual_departed_at IS NOT NULL AND actual_returned_at IS NULL")
    int returnFromOuting(@Param("outingNo") String outingNo,
                         @Param("at") LocalDateTime at,
                         @Param("locationCiphertext") String locationCiphertext,
                         @Param("photoKey") String photoKey,
                         @Param("operator") String operator);
}
