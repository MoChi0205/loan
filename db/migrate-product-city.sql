-- ============================================================
-- 产品-服务城市关系（市一级，省市名称字符串）
-- 规范：无物理外键，以 product_code 业务唯一键关联；物理主键自增 id 仅供内部。
-- 用途：企业申请填「申请城市」，匹配时按城市精确筛选产品。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_bank_product_city` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键(自增,不作业务关联)',
  `product_city_code` varchar(16) NOT NULL COMMENT '产品城市关系业务编码(pcity+小写字母数字,总长16)',
  `product_code` varchar(64) NOT NULL COMMENT '产品业务唯一键(t_bank_product.product_code)',
  `province` varchar(64) NOT NULL COMMENT '省(名称字符串)',
  `city` varchar(64) NOT NULL COMMENT '市(名称字符串,市一级)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_city_code` (`product_city_code`),
  UNIQUE KEY `uk_product_city` (`product_code`, `province`, `city`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行产品-服务城市';
