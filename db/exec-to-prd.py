#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
loan_db 建库 + 执行 DDL/初始化数据到 prd MySQL。
用法: python exec-to-prd.py
连接: 110.42.219.5:9306 · root/CHANGE_ME_ROOT（建库授权）+ erp_user（业务）
"""
import pymysql

HOST = "110.42.219.5"
PORT = 9306
ROOT_PWD = "CHANGE_ME_ROOT"
DB = "loan_db"
APP_USER = "erp_user"


def split_sql(content):
    """状态机拆分 SQL：正确处理字符串/反引号/注释内的分号。"""
    statements = []
    buf = []
    in_single = in_double = in_backtick = False
    in_line_comment = in_block_comment = False
    i, n = 0, len(content)
    while i < n:
        ch = content[i]
        nxt = content[i + 1] if i + 1 < n else ""

        # 块注释 /* */
        if not in_single and not in_double and not in_backtick and ch == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue
        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
            else:
                i += 1
            continue

        # 行注释 --
        if not in_single and not in_double and not in_backtick and ch == "-" and nxt == "-":
            in_line_comment = True
            i += 2
            continue
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue

        # 字符串 / 反引号
        if ch == "'" and not in_double and not in_backtick:
            in_single = not in_single
            buf.append(ch)
            if in_single and nxt == "'":  # 转义 ''
                buf.append(nxt)
                i += 2
                continue
        elif ch == '"' and not in_single and not in_backtick:
            in_double = not in_double
            buf.append(ch)
        elif ch == "`" and not in_single and not in_double:
            in_backtick = not in_backtick
            buf.append(ch)
        elif ch == ";" and not in_single and not in_double and not in_backtick:
            stmt = "".join(buf).strip()
            if stmt and not stmt.upper().startswith("SET "):
                statements.append(stmt)
            buf = []
        else:
            buf.append(ch)
        i += 1

    tail = "".join(buf).strip()
    if tail and not tail.upper().startswith("SET "):
        statements.append(tail)
    return statements


def run_file(cur, path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    stmts = split_sql(content)
    errors = []
    for s in stmts:
        try:
            cur.execute(s)
        except Exception as e:
            errors.append((s[:70].replace("\n", " "), str(e)[:140]))
    return len(stmts), errors


def main():
    root = pymysql.connect(host=HOST, port=PORT, user="root", password=ROOT_PWD,
                           autocommit=True, connect_timeout=15)
    rc = root.cursor()
    rc.execute("CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci" % DB)
    rc.execute("GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%'" % (DB, APP_USER))
    rc.execute("FLUSH PRIVILEGES")
    print("[1/3] loan_db 建库 + 授权 erp_user 完成")
    rc.close()
    root.close()

    conn = pymysql.connect(host=HOST, port=PORT, user="root", password=ROOT_PWD,
                           database=DB, autocommit=True, connect_timeout=15)
    cur = conn.cursor()

    n1, e1 = run_file(cur, "db/loan-db-schema.sql")
    print("[2/3] DDL 执行 %d 条，错误 %d 条" % (n1, len(e1)))
    for s, err in e1:
        print("   ERR:", s, "=>", err)

    n2, e2 = run_file(cur, "db/init-data.sql")
    print("[3/3] 初始化数据执行 %d 条，错误 %d 条" % (n2, len(e2)))
    for s, err in e2:
        print("   ERR:", s, "=>", err)

    cur.close()
    conn.close()
    print("全部完成" if not e1 and not e2 else "存在错误，请检查上方输出")


if __name__ == "__main__":
    main()
