#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从所选 Nacos namespace 读取数据源后执行迁移。

红线：必须显式提供 NACOS_SERVER_ADDR、NACOS_NAMESPACE；不回退到本地 MySQL、默认密码
或项目文件中的数据源。远程执行还必须显式传入 --confirm-remote。

用法：
  NACOS_SERVER_ADDR=host:8848 NACOS_NAMESPACE=prd \
    python scripts/apply-sql-nacos.py --confirm-remote --allow-placeholder-password db/migrate-*.sql
"""
import argparse
import json
import os
import re
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

import pymysql
from pymysql.constants import CLIENT


def required_env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"必须显式设置 {name}，禁止回退到本地默认值")
    return value


def nacos_base(addr):
    addr = addr.rstrip("/")
    if not re.match(r"^https?://", addr):
        addr = "http://" + addr
    return addr + "/nacos/v1/cs/configs"


def fetch_config(addr, namespace, group, data_id):
    base_addr = addr.rstrip("/")
    if not re.match(r"^https?://", base_addr):
        base_addr = "http://" + base_addr
    params = urlencode({"dataId": data_id, "group": group, "tenant": namespace})
    url = nacos_base(base_addr) + "?" + params
    headers = {}
    username = os.environ.get("NACOS_USERNAME", "").strip()
    password = os.environ.get("NACOS_PASSWORD", "").strip()
    if username and password:
        login_params = urlencode({"username": username, "password": password})
        login_url = base_addr + "/nacos/v1/auth/login"
        req = Request(login_url, data=login_params.encode(), method="POST")
        try:
            with urlopen(req, timeout=15) as response:
                token = json.loads(response.read().decode("utf-8")).get("accessToken")
                if token:
                    params += "&accessToken=" + token
                    url = nacos_base(base_addr) + "?" + params
        except (HTTPError, URLError, ValueError) as exc:
            raise RuntimeError(f"Nacos 登录失败: {exc}") from exc
    try:
        with urlopen(Request(url, headers=headers), timeout=15) as response:
            return response.read().decode("utf-8")
    except (HTTPError, URLError) as exc:
        raise RuntimeError(f"读取 Nacos 配置失败: {exc}") from exc


def parse_properties(content):
    values = {}
    for raw in content.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def load_dsn(allow_placeholder=False):
    addr = required_env("NACOS_SERVER_ADDR")
    namespace = required_env("NACOS_NAMESPACE")
    group = os.environ.get("NACOS_GROUP", "loan").strip() or "loan"
    content = fetch_config(addr, namespace, group, "application.properties")
    props = parse_properties(content)
    url = props.get("spring.datasource.url", "")
    user = props.get("spring.datasource.username", "")
    password = props.get("spring.datasource.password", "")
    if not url or not user:
        raise RuntimeError("所选 Nacos 配置缺少 spring.datasource.url/username")
    match = re.match(r"jdbc:mysql://([^/:]+)(?::(\d+))?/([^?]+)", url)
    if not match:
        raise RuntimeError("仅支持从 Nacos 读取 MySQL JDBC URL")
    host, port, database = match.group(1), int(match.group(2) or 3306), match.group(3)
    if not password:
        raise RuntimeError("所选 Nacos 数据库密码仍为占位符，拒绝执行迁移")
    if password.startswith(("CHANGE_ME_", "PLACEHOLDER_")) and not allow_placeholder:
        raise RuntimeError("所选 Nacos 数据库密码仍为占位符；如用户已明确确认其为测试密码，请加 --allow-placeholder-password")
    return addr, namespace, host, port, database, user, password


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--confirm-remote", action="store_true", help="确认对所选 Nacos 数据源执行")
    parser.add_argument("--allow-placeholder-password", action="store_true",
                        help="明确确认 Nacos 中的 CHANGE_ME_/PLACEHOLDER_ 值就是测试密码")
    parser.add_argument("files", nargs="+", help="SQL 文件")
    args = parser.parse_args()
    if not args.confirm_remote:
        parser.error("远程数据源执行必须显式提供 --confirm-remote")
    try:
        addr, namespace, host, port, database, user, password = load_dsn(
            allow_placeholder=args.allow_placeholder_password)
    except RuntimeError as exc:
        print(f"拒绝执行：{exc}", file=sys.stderr)
        return 2
    print(f"目标：Nacos {addr} / namespace={namespace} / database={host}:{port}/{database}")
    conn = pymysql.connect(host=host, port=port, user=user, password=password,
                           database=database, charset="utf8mb4", autocommit=False,
                           client_flag=CLIENT.MULTI_STATEMENTS)
    ok = 0
    try:
        for item in args.files:
            if not os.path.exists(item):
                print(f"SKIP  {item}（文件不存在）")
                continue
            with open(item, encoding="utf-8") as fh:
                sql = fh.read()
            try:
                with conn.cursor() as cur:
                    cur.execute(sql)
                conn.commit()
                ok += 1
                print(f"OK    {item}")
            except Exception as exc:
                conn.rollback()
                print(f"FAIL  {item} -> {exc}", file=sys.stderr)
                return 1
    finally:
        conn.close()
    print(f"完成：成功 {ok}/{len(args.files)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
