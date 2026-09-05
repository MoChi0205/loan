#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对远程 loan_db 执行客户回收迁移（t_client_profile 加列 + t_client_recycle_config）。

脚本自行读取 /tmp/prd_cfg2.txt 中的 datasource 配置，不向外打印任何凭据。
"""
import re
import pymysql

CFG_PATH = "/tmp/prd_cfg2.txt"


def load_dsn():
    text = open(CFG_PATH, encoding="utf-8").read()
    url = None
    user = None
    pwd = None
    for line in text.splitlines():
        if line.strip().startswith("spring.datasource.url"):
            url = line.split("=", 1)[1].strip().strip("'\"")
        elif line.strip().startswith("spring.datasource.username"):
            user = line.split("=", 1)[1].strip().strip("'\"")
        elif line.strip().startswith("spring.datasource.password"):
            pwd = line.split("=", 1)[1].strip().strip("'\"")
    m = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([?\w]+)", url)
    host, port, db = m.group(1), int(m.group(2)), re.sub(r"\?.*$", "", m.group(3))
    return host, port, db, user, pwd


def col_exists(conn, table, col):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT 1 FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND COLUMN_NAME=%s",
            (conn.db, table, col),
        )
        return cur.fetchone() is not None


def main():
    host, port, db, user, pwd = load_dsn()
    conn = pymysql.connect(host=host, port=port, user=user, password=pwd,
                           database=db, charset="utf8mb4", autocommit=False)
    try:
        added = []
        if not col_exists(conn, "t_client_profile", "last_followed_at"):
            conn.cursor().execute(
                "ALTER TABLE t_client_profile ADD COLUMN last_followed_at datetime DEFAULT NULL "
                "COMMENT '最后跟进时间(超期回收判定基准;归属/转移/跟进刷新)'")
            added.append("last_followed_at")
        if not col_exists(conn, "t_client_profile", "assign_blocked_until"):
            conn.cursor().execute(
                "ALTER TABLE t_client_profile ADD COLUMN assign_blocked_until datetime DEFAULT NULL "
                "COMMENT '回收冷却到期时间(回收进公海后原归属人不可认领/不可被直接分配)'")
            added.append("assign_blocked_until")

        conn.cursor().execute(
            "CREATE TABLE IF NOT EXISTS t_client_recycle_config ("
            "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
            "  config_key VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '配置键(当前仅 GLOBAL 单行)',"
            "  recycle_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '回收开关(1开/0关)',"
            "  recycle_days INT NOT NULL DEFAULT 30 COMMENT '回收天数(超过该天数无跟进自动回收进公海)',"
            "  warn_days INT NOT NULL DEFAULT 3 COMMENT '预警天数(距回收剩余时站内预警归属人)',"
            "  cooldown_days INT NOT NULL DEFAULT 7 COMMENT '冷却天数(回收后原归属人不可认领)',"
            "  updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',"
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
            "  PRIMARY KEY (id),"
            "  UNIQUE KEY uk_config_key (config_key)"
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci "
            "COMMENT='客户回收规则参数(全参数化不写死;参考 t_lead_recycle_config 与 tse 资源池回收)'")

        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM t_client_recycle_config WHERE config_key='GLOBAL'")
            if cur.fetchone()[0] == 0:
                cur.execute(
                    "INSERT INTO t_client_recycle_config "
                    "(config_key, recycle_enabled, recycle_days, warn_days, cooldown_days) "
                    "VALUES ('GLOBAL', 1, 30, 3, 7)")
        conn.commit()
        print("OK columns_added=%s config_seeded=true" % (added or "none"))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
