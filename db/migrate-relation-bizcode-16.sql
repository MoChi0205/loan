-- =====================================================================
-- 渠道名单 / 产品城市关系业务编码迁移（用户确认：总长度固定 16）
--   t_channel_user_list.list_code        = culist + 10 位小写字母数字
--   t_bank_product_city.product_city_code = pcity + 11 位小写字母数字
--
-- 执行顺序：加可空列 → 回填 → 校验 → NOT NULL → 唯一索引。
-- 脚本通过 information_schema 判断列/索引，可重复执行。
-- =====================================================================

SET @schema_name = DATABASE();

-- 1. t_channel_user_list.list_code
SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 't_channel_user_list' AND column_name = 'list_code'),
    'SELECT 1',
    'ALTER TABLE t_channel_user_list ADD COLUMN list_code VARCHAR(16) NULL COMMENT ''名单记录业务编码(culist+小写字母数字,总长16)'' AFTER id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_channel_user_list
SET list_code = CONCAT('culist', LEFT(MD5(CONCAT(UUID(), ':', id)), 10))
WHERE list_code IS NULL OR list_code = '';

SET @invalid_count = (SELECT COUNT(*) FROM t_channel_user_list
                      WHERE list_code IS NULL OR CHAR_LENGTH(list_code) <> 16 OR list_code NOT REGEXP '^culist[0-9a-z]{10}$');
SET @duplicate_count = (SELECT COUNT(*) FROM (
    SELECT list_code FROM t_channel_user_list GROUP BY list_code HAVING COUNT(*) > 1
) duplicated);
SET @assert_sql = IF(@invalid_count = 0 AND @duplicate_count = 0,
                     'SELECT ''t_channel_user_list 回填校验通过'' AS result',
                     'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_channel_user_list 业务编码回填校验失败''');
PREPARE stmt FROM @assert_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE t_channel_user_list MODIFY COLUMN list_code VARCHAR(16) NOT NULL
    COMMENT '名单记录业务编码(culist+小写字母数字,总长16)';

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name AND table_name = 't_channel_user_list' AND index_name = 'uk_list_code'),
    'SELECT 1',
    'ALTER TABLE t_channel_user_list ADD UNIQUE KEY uk_list_code (list_code)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. t_bank_product_city.product_city_code
SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 't_bank_product_city' AND column_name = 'product_city_code'),
    'SELECT 1',
    'ALTER TABLE t_bank_product_city ADD COLUMN product_city_code VARCHAR(16) NULL COMMENT ''产品城市关系业务编码(pcity+小写字母数字,总长16)'' AFTER id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_bank_product_city
SET product_city_code = CONCAT('pcity', LEFT(MD5(CONCAT(UUID(), ':', id)), 11))
WHERE product_city_code IS NULL OR product_city_code = '';

SET @invalid_count = (SELECT COUNT(*) FROM t_bank_product_city
                      WHERE product_city_code IS NULL OR CHAR_LENGTH(product_city_code) <> 16
                         OR product_city_code NOT REGEXP '^pcity[0-9a-z]{11}$');
SET @duplicate_count = (SELECT COUNT(*) FROM (
    SELECT product_city_code FROM t_bank_product_city GROUP BY product_city_code HAVING COUNT(*) > 1
) duplicated);
SET @assert_sql = IF(@invalid_count = 0 AND @duplicate_count = 0,
                     'SELECT ''t_bank_product_city 回填校验通过'' AS result',
                     'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_bank_product_city 业务编码回填校验失败''');
PREPARE stmt FROM @assert_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE t_bank_product_city MODIFY COLUMN product_city_code VARCHAR(16) NOT NULL
    COMMENT '产品城市关系业务编码(pcity+小写字母数字,总长16)';

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name AND table_name = 't_bank_product_city' AND index_name = 'uk_product_city_code'),
    'SELECT 1',
    'ALTER TABLE t_bank_product_city ADD UNIQUE KEY uk_product_city_code (product_city_code)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '业务编码迁移完成' AS result,
       (SELECT COUNT(*) FROM t_channel_user_list) AS channel_list_rows,
       (SELECT COUNT(*) FROM t_bank_product_city) AS product_city_rows;
