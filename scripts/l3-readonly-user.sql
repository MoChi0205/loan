-- =============================================================================
-- L3 集成测试「只读」账号（推荐）：在云上 MySQL 执行，供集成测试以只读方式接生产库
-- 用法（在云服务器或有权限的客户端执行）：
--   mysql -h 110.42.219.5 -P 9306 -u root -p loan_db < scripts/l3-readonly-user.sql
-- 说明：
--   * 仅授予 SELECT / SHOW VIEW / PROCESS，无任何写权限；
--   * 配合 application-l3.properties 的 spring.datasource.username=${LOAN_PRD_DB_USER:erp_ro}
--     运行 L3 前 export LOAN_PRD_DB_USER=erp_ro 与 LOAN_PRD_DB_PASSWORD=<强密码> 即可。
--   * 若云库已提供只读副本，请直接用它，跳过本脚本。
-- =============================================================================

CREATE USER IF NOT EXISTS 'erp_ro'@'%' IDENTIFIED BY 'CHANGE_ME_READONLY_STRONG';

-- 仅读权限（不含 grant option）
GRANT SELECT, SHOW VIEW, PROCESS
  ON loan_db.* TO 'erp_ro'@'%';

FLUSH PRIVILEGES;
