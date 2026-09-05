#!/usr/bin/env bash
# ============================================================
# loan 后端本地 dev 启动脚本：连本地 Docker 起的 Nacos（127.0.0.1:8848，namespace=dev）
#
# 前置：
#   1) docker compose up -d（起 MySQL + Redis + Nacos）
#   2) 导入 dev 配置到 Nacos：
#        NACOS_SERVER_ADDR=127.0.0.1:8848 bash nacos/scripts/import-config.sh dev
#   （首次需在 Nacos 控制台创建命名空间 dev；或运行下方自动创建逻辑）
#
# 用法：bash scripts/run-dev-local.sh
# ============================================================
set -e

cd "$(dirname "$0")/.."

JVM_ARGS="-Dnacos.server-addr=127.0.0.1:8848 -Dnacos.namespace=dev -Dspring.cloud.nacos.discovery.register-enabled=false -Ddubbo.enabled=false -Dapp.gateway.trust-only=false"

echo ">>> 安装 loan-api（首次需 install 供 loan-service 解析）"
mvn -pl loan-api -am install -DskipTests -q

echo ">>> 启动 loan-service：连本地 dev Nacos(127.0.0.1:8848) · namespace=dev · 不注册服务 · 关 Dubbo"
mvn -pl loan-service spring-boot:run -DskipTests -Dspring-boot.run.jvmArguments="${JVM_ARGS}"
