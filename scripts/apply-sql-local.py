#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在本地 dev 库（127.0.0.1:3306/loan_db）执行指定的 SQL 文件。

用法：python scripts/apply-sql-local.py <file1.sql> [file2.sql ...]

特点：
- 开启 CLIENT.MULTI_STATEMENTS，整文件一次执行，避免按分号切分破坏字符串内的语句；
- 逐文件提交，失败即停并打印原因，已成功的文件不受影响；
- 凭据只从 nacos/config/dev/application.properties 读取，不打印明文。
"""
import os
import sys

import pymysql
from pymysql.constants import CLIENT

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEV_PROPS = os.path.join(ROOT, "nacos", "config", "dev", "application.properties")


def load_dsn():
    url = user = pwd = None
    with open(DEV_PROPS, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("spring.datasource.url="):
                url = line.split("=", 1)[1].strip()
            elif line.startswith("spring.datasource.username="):
                user = line.split("=", 1)[1].strip()
            elif line.startswith("spring.datasource.password="):
                pwd = line.split("=", 1)[1].strip()
    host, port, db = "127.0.0.1", 3306, "loan_db"
    if url:
        body = url.split("://", 1)[1].split("?", 1)[0]
        addr, db = body.rsplit("/", 1)
        if ":" in addr:
            host, port = addr.split(":", 1)
            port = int(port)
        else:
            host = addr
    return host, port, db, user, pwd


def main():
    files = sys.argv[1:]
    if not files:
        print("用法: python scripts/apply-sql-local.py <file.sql> [...]")
        return 1
    host, port, db, user, pwd = load_dsn()
    conn = pymysql.connect(
        host=host, port=port, user=user, password=pwd, database=db,
        charset="utf8mb4", autocommit=False,
        client_flag=CLIENT.MULTI_STATEMENTS,
    )
    ok = 0
    try:
        for item in files:
            path = item if os.path.isabs(item) else os.path.join(ROOT, item)
            if not os.path.exists(path):
                print(f"SKIP  {item} (文件不存在)")
                continue
            with open(path, encoding="utf-8") as fh:
                sql = fh.read()
            try:
                with conn.cursor() as cur:
                    cur.execute(sql)
                conn.commit()
                ok += 1
                print(f"OK    {item}")
            except Exception as exc:
                conn.rollback()
                print(f"FAIL  {item} -> {exc}")
                return 1
    finally:
        conn.close()
    print(f"完成：成功 {ok}/{len(files)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
