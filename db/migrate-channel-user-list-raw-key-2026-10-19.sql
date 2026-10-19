-- 渠道名单键明文回显（2026-10-19）
-- 背景：名单键个人=手机号MD5、企业=信用代码，列表无法辨识具体号码/编码；
-- 新增 raw_key 明文列用于回显，个人存手机号原文、企业存信用代码原文。
-- 命中匹配仍以 list_key（个人MD5/企业原文）为准，不受影响。

ALTER TABLE t_channel_user_list
    ADD COLUMN raw_key VARCHAR(64) DEFAULT NULL COMMENT '名单键明文（个人=手机号原文；企业=统一社会信用代码原文）' AFTER list_key;

-- 企业存量回填：企业 list_key 即信用代码原文，可直接复制。
UPDATE t_channel_user_list
SET raw_key = list_key
WHERE customer_group = 'ENTERPRISE'
  AND (raw_key IS NULL OR raw_key = '');

-- 个人存量：MD5 不可逆，无法回填，新增/编辑后才有明文；历史记录 raw_key 保持 NULL（前端回退展示 list_key）。
