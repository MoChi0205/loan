#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
schema 文档基线与生产库核对脚本（D83-六批，2026-10-21）。

背景：db/loan-db-schema.sql 曾与生产库结构性偏差 76 处 / 缺 7 张表，导致
insert 漏设 NOT NULL 无默认列时报 "Field 'xxx' doesn't have a default value"。
本脚本把「核对」变成可重复动作，避免再次拿过期文档当真理。

用法：
    python3 scripts/schema-diff-against-live.py [--namespace prd] [--group loan]
                                               [--data-id application.properties]
                                               [--schema db/loan-db-schema.sql]

依赖：pymysql（装到受管 venv：~/.workbuddy/binaries/python/envs/default/bin/pip install pymysql）

数据源从 Nacos 配置读取（不写死库地址），与 loan-service 启动口径一致；
密码只在内存中使用，不打印、不落盘。
"""
import argparse
import json
import re
import sys
import urllib.request

NACOS_DEFAULT = "124.221.150.239:9848"


def fetch_nacos_config(addr, data_id, group, namespace):
    url = (f"http://{addr}/nacos/v1/cs/configs?dataId={urllib.request.quote(data_id)}"
           f"&group={urllib.request.quote(group)}&tenant={urllib.request.quote(namespace)}")
    with urllib.request.urlopen(url, timeout=20) as resp:
        text = resp.read().decode("utf-8")
    if not text.strip():
        sys.exit(f"[FAIL] Nacos 无配置：{url}")
    cfg = dict(re.findall(r"^([\w.\-]+)\s*=\s*(.*)$", text, re.M))
    return cfg


def parse_doc_schema(path):
    """解析文档中的 CREATE TABLE，返回 {table: [{column,type,nullable,default}]}"""
    sql = open(path, encoding="utf-8").read()
    doc = {}
    for m in re.finditer(r"CREATE TABLE\s+`?(\w+)`?\s*\((.*?)\n\)\s*ENGINE", sql, re.S | re.I):
        tname, body = m.group(1), m.group(2)
        cols = []
        for line in body.split("\n"):
            line = line.strip().rstrip(",")
            cm = re.match(r"`(\w+)`\s+([\w]+)(\([^)]*\))?(.*)$", line)
            if cm:
                rest = cm.group(4) or ""
                dm = re.search(r"DEFAULT\s+([^\s,]+)", rest, re.I)
                cols.append(dict(column=cm.group(1), type=(cm.group(2) + (cm.group(3) or "")).lower(),
                                 nullable="NOT NULL" not in rest.upper(),
                                 default=dm.group(1) if dm else None))
        doc[tname] = cols
    return doc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--namespace", default="prd")
    ap.add_argument("--group", default="loan")
    ap.add_argument("--data-id", default="application.properties")
    ap.add_argument("--nacos", default=NACOS_DEFAULT)
    ap.add_argument("--schema", default="db/loan-db-schema.sql")
    args = ap.parse_args()

    import pymysql  # 延迟导入，--help 时不依赖驱动

    cfg = fetch_nacos_config(args.nacos, args.data_id, args.group, args.namespace)
    m = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([\w]+)", cfg.get("spring.datasource.url", ""))
    if not m:
        sys.exit("[FAIL] Nacos 配置缺少 spring.datasource.url")
    host, port, db = m.group(1), int(m.group(2)), m.group(3)

    conn = pymysql.connect(host=host, port=port, user=cfg["spring.datasource.username"],
                           password=cfg["spring.datasource.password"], database=db,
                           charset="utf8mb4", connect_timeout=15)
    cur = conn.cursor()

    cur.execute("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT "
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s "
                "ORDER BY TABLE_NAME, ORDINAL_POSITION", (db,))
    live = {}
    for t, c, ct, nullable, default in cur.fetchall():
        live.setdefault(t, []).append(dict(column=c, type=ct.lower(), nullable=(nullable == "YES"), default=default))

    # NOT NULL 且无默认（不含自增）：insert 漏设即报错的列
    # 注意：pymysql 参数化时 LIKE 里的 % 必须写成 %%，否则被当占位符报错
    cur.execute("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS "
                "WHERE TABLE_SCHEMA=%s AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL "
                "AND EXTRA NOT LIKE '%%auto_increment%%' ORDER BY TABLE_NAME, ORDINAL_POSITION", (db,))
    notnull = {}
    for t, c, ct in cur.fetchall():
        notnull.setdefault(t, []).append(f"{c} {ct}")
    cur.close()
    conn.close()

    doc = parse_doc_schema(args.schema)

    print(f"数据源: {host}:{port}/{db}（namespace={args.namespace} group={args.group}）")
    print(f"文档表数 {len(doc)} / 生产表数 {len(live)} / 文档列数 "
          f"{sum(len(v) for v in doc.values())} / 生产列数 {sum(len(v) for v in live.values())}")

    only_doc = sorted(set(doc) - set(live))
    only_live = sorted(set(live) - set(doc))
    if only_doc:
        print(f"\n[警告] 文档有、生产没有的表: {only_doc}")
    if only_live:
        print(f"[提示] 生产有、文档没有的表（需补文档）: {only_live}")

    issues = []
    for t in sorted(set(doc) & set(live)):
        d = {c["column"]: c for c in doc[t]}
        l = {c["column"]: c for c in live[t]}
        for c in sorted(set(d) - set(l)):
            issues.append(f"{t}.{c}: 文档有、生产无")
        for c in sorted(set(l) - set(d)):
            issues.append(f"{t}.{c}: 生产有、文档无 ({l[c]['type']})")
        for c in sorted(set(d) & set(l)):
            if d[c]["nullable"] != l[c]["nullable"]:
                issues.append(f"{t}.{c}: 可空性不一致(文档{'可空' if d[c]['nullable'] else 'NOT NULL'}"
                              f" / 生产{'可空' if l[c]['nullable'] else 'NOT NULL'})")

    if issues:
        print(f"\n[不一致] {len(issues)} 处：")
        for i in issues:
            print("  -", i)
    else:
        print("\n[OK] 文档基线与生产库结构一致")

    print(f"\n[NOT NULL 无默认列] {sum(len(v) for v in notnull.values())} 个 / {len(notnull)} 张表"
          "（insert 这些表时必须显式赋值，MyBatis-Plus 会跳过 null 字段）：")
    for t, cols in sorted(notnull.items()):
        print(f"  {t}: {', '.join(cols)}")

    print("\n[建议] 文档基线若落后，用 SHOW CREATE TABLE 重新生成 db/loan-db-schema.sql。")
    return 0 if not issues else 1


if __name__ == "__main__":
    sys.exit(main())
