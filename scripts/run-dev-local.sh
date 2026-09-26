#!/usr/bin/env bash
# ============================================================
# loan 本机开发启动脚本：本机只运行应用，基础设施全部从指定 Nacos 获取。
# 禁止启动/依赖本地 MySQL、Redis、Nacos、OSS 或 Docker。
#
# 用法：
#   NACOS_SERVER_ADDR=<host:port> NACOS_NAMESPACE=<namespace> bash scripts/run-dev-local.sh
# ============================================================
set -e

cd "$(dirname "$0")/.."

: "${NACOS_SERVER_ADDR:?必须显式设置 NACOS_SERVER_ADDR，禁止回退本地 Nacos}"
: "${NACOS_NAMESPACE:?必须显式设置 NACOS_NAMESPACE}"

JVM_ARGS="-Dnacos.server-addr=${NACOS_SERVER_ADDR} -Dnacos.namespace=${NACOS_NAMESPACE} -Dspring.cloud.nacos.discovery.register-enabled=false -Ddubbo.enabled=false -Dapp.gateway.trust-only=false -Dloan.auth.dev-sms-code-visible=true"

echo ">>> 安装 loan-api（首次需 install 供 loan-service 解析）"
mvn -pl loan-api -am install -DskipTests -q

echo ">>> 启动 loan-service：Nacos(${NACOS_SERVER_ADDR}) · namespace=${NACOS_NAMESPACE} · 不注册服务 · 关 Dubbo"
mvn -pl loan-service spring-boot:run -DskipTests -Dspring-boot.run.jvmArguments="${JVM_ARGS}"
