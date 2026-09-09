package com.loan.importing.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("t_batch_import_task") public class BatchImportTask { @TableId(type=IdType.AUTO) private Long id; private String taskNo,importType,status,applicantStaffCode,errorMessage; private Integer totalCount,successCount,failedCount,progressPercent; private LocalDateTime createdAt,updatedAt; }
