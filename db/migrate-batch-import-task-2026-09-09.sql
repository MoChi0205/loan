CREATE TABLE IF NOT EXISTS t_batch_import_task (
 id BIGINT NOT NULL AUTO_INCREMENT, task_no VARCHAR(64) NOT NULL, import_type VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', total_count INT NOT NULL DEFAULT 0, success_count INT NOT NULL DEFAULT 0,
 failed_count INT NOT NULL DEFAULT 0, progress_percent INT NOT NULL DEFAULT 0, applicant_staff_code VARCHAR(64) NOT NULL,
 error_message VARCHAR(500), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_batch_task_no(task_no), KEY idx_batch_task_staff(status, applicant_staff_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_batch_import_task_detail (
 id BIGINT NOT NULL AUTO_INCREMENT, task_no VARCHAR(64) NOT NULL, row_no INT NOT NULL, status VARCHAR(20) NOT NULL,
 error_message VARCHAR(500), result_json TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_batch_task_row(task_no,row_no), KEY idx_batch_task_detail(task_no,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
