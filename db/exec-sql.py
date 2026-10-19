#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对 loan_db 执行指定 SQL 文件（凭据从 Nacos 快照读取，不打印任何凭据）。
用法: python exec-sql.py <file1.sql> [file2.sql ...]
"""
import re
import sys
import pymysql

CFG = "/Users/admin/nacos/config/fixed-prd-124.221.150.239_9848_nacos/snapshot-tenant/prd/loan/application.properties"


def load_dsn():
    text = open(CFG, encoding="utf-8").read()
    m = re.search(r"spring\.datasource\.url=jdbc:mysql://([^:/]+):(\d+)/(\w+)", text)
    user = re.search(r"spring\.datasource\.username=(.+)", text).group(1).strip()
    pwd = re.search(r"spring\.datasource\.password=(.+)", text).group(1).strip()
    return m.group(1), int(m.group(2)), m.group(3), user, pwd


def split_sql(content):
    """状态机拆分 SQL：正确处理字符串/反引号/注释内的分号。"""
    statements, buf = [], []
    in_single = in_double = in_backtick = False
    in_line_comment = in_block_comment = False
    i, n = 0, len(content)
    while i < n:
        ch, nxt = content[i], content[i + 1] if i + 1 < n else ""
        if not in_single and not in_double and not in_backtick and ch == "/" and nxt == "*":
            in_block_comment = True; i += 2; buf.append("/*"); continue
        if in_block_comment:
            buf.append(ch)
            if ch == "*" and nxt == "/":
                in_block_comment = False; buf.append("/"); i += 2; continue
            i += 1; continue
        if not in_block_comment and not in_single and not in_double and not in_backtick and ch == "-" and nxt == "-":
            in_line_comment = True; i += 2; continue
        if in_line_comment:
            if ch == "\n": in_line_comment = False
            i += 1; continue
        buf.append(ch)
        if ch == "'" and not in_double and not in_backtick:
            if in_single and nxt == "'": buf.append(nxt); i += 2; continue
            in_single = not in_single
        elif ch == '"' and not in_single and not in_backtick:
            in_double = not in_double
        elif ch == "`" and not in_single and not in_double:
            in_backtick = not in_backtick
        elif ch == ";" and not in_single and not in_double and not in_backtick:
            stmt = "".join(buf).strip()
            if stmt: statements.append(stmt)
            buf = []
        i += 1
    tail = "".join(buf).strip()
    if tail: statements.append(tail)
    return statements


def main():
    host, port, db, user, pwd = load_dsn()
    conn = pymysql.connect(host=host, port=port, database=db, user=user, password=pwd,
                           charset="utf8mb4", autocommit=False)
    try:
        for path in sys.argv[1:]:
            content = open(path, encoding="utf-8").read()
            stmts = split_sql(content)
            print(f"== {path}: {len(stmts)} statements")
            with conn.cursor() as cur:
                for idx, stmt in enumerate(stmts, 1):
                    head = " ".join(stmt.split())[:90]
                    try:
                        cur.execute(stmt)
                        conn.commit()
                        if not head.upper().startswith("SELECT"):
                            print(f"  [{idx}] OK  {head}")
                    except Exception as e:
                        conn.rollback()
                        print(f"  [{idx}] FAIL {head}\n      -> {e}")
                        raise SystemExit(1)
        print("ALL DONE")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
