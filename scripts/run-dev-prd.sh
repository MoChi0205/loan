#!/usr/bin/env bash
# ============================================================
# loan 后端开发启动脚本：不连本地环境，直连 prd Nacos 拉配置
#
# 启动参数：
#   -Dnacos.server-addr=124.221.150.239:9848       prd Nacos（HTTP 映射 9848→容器内 8848）
#   -Dnacos.namespace=prd                            prd 命名空间
#   -Dspring.cloud.nacos.discovery.register-enabled=false  不向 Nacos 注册服务
#   -Ddubbo.enabled=false                            关闭 Dubbo
#   -Dapp.gateway.trust-only=false                   网关不强制信任头
#
# 用法：bash scripts/run-dev-prd.sh [模块名，默认 loan-service]
# 前置：prd Nacos 已导入 loan group 配置
#   NACOS_SERVER_ADDR=124.221.150.239:9848 bash nacos/scripts/import-config.sh prd
# ============================================================
set -e

MODULE="${1:-loan-service}"
cd "$(dirname "$0")/.."
BASE_DIR="$(pwd)"

# ============================================================
# JDK 8 探测与 service.sh 共用同一实现，避免两个入口选择结果不一致。
# 校验版本与二进制架构；Apple 芯片优先原生 arm64 JDK 8。
# ============================================================
source "$BASE_DIR/scripts/lib/java8.sh"
JAVA_HOME="$(loan_detect_java8)" || exit 1
export JAVA_HOME
loan_print_java8_summary "$JAVA_HOME"

JVM_ARGS="-Dnacos.server-addr=124.221.150.239:9848 -Dnacos.namespace=prd -Dspring.cloud.nacos.discovery.register-enabled=false -Ddubbo.enabled=false -Dapp.gateway.trust-only=false"

echo ">>> 安装依赖模块 loan-api（首次需 install 供 loan-service 解析）"
mvn -pl loan-api -am install -DskipTests -q

echo ">>> 启动 ${MODULE}：直连 prd Nacos(124.221.150.239:9848) · namespace=prd · 不注册服务 · 关 Dubbo"
mvn -pl "${MODULE}" spring-boot:run -DskipTests -Dspring-boot.run.jvmArguments="${JVM_ARGS}"
