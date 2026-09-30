package com.loan.resourcepool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_staff_data_transfer_record")
public class StaffDataTransferRecord implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transferNo;
    private String fromStaffCode;
    private String toStaffCode;
    private String businessType;
    private String businessCode;
    private String reason;
    private String operator;
    private LocalDateTime createdAt;
}
