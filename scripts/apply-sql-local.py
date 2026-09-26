#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""已停用本地数据库迁移入口。

项目红线要求迁移必须读取当前启动所选 Nacos 配置，禁止回退到
127.0.0.1:3306/loan_db。请改用 scripts/apply-sql-nacos.py。
"""
import sys


def main():
    print("已拒绝：本地数据库迁移入口已停用。请使用 scripts/apply-sql-nacos.py，显式提供 Nacos 地址、命名空间和 --confirm-remote。", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
