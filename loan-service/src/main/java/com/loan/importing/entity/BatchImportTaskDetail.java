package com.loan.importing.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("t_batch_import_task_detail") public class BatchImportTaskDetail { @TableId(type=IdType.AUTO) private Long id; private String taskNo,status,errorMessage,resultJson; private Integer rowNo; private LocalDateTime createdAt; }
