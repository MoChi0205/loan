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

# ============================================================
# JDK8 自动探测：环境变量 JAVA_HOME 可能失效（如 ~/.bash_profile
# 写死旧路径且二进制无执行权限/架构不符），这里通过"实际执行
# java -version"验证候选 JDK 是否真能跑，保证任何环境都能启动。
# 优先级：已知可用固定 JDK > 系统注册 JDK > 继承的 $JAVA_HOME > PATH
# ============================================================
detect_java8() {
  local candidates h
  candidates=(
    "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home"
    "$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
    "$JAVA_HOME"
  )
  for h in "${candidates[@]}"; do
    [ -z "$h" ] && continue
    if [ -x "$h/bin/java" ] && "$h/bin/java" -version >/dev/null 2>&1 \
      && "$h/bin/java" -version 2>&1 | grep -q '1\.8'; then
      echo "$h"; return 0
    fi
  done
  # 兜底：从 PATH 里解析 java 的上级目录
  local j
  j=$(command -v java 2>/dev/null || true)
  if [ -n "$j" ] && "$j" -version >/dev/null 2>&1 && "$j" -version 2>&1 | grep -q '1\.8'; then
    echo "$(cd "$(dirname "$j")/.." && pwd)"; return 0
  fi
  return 1
}

JAVA_HOME=$(detect_java8) || { echo "ERROR: 未找到可用的 JDK 8，请先安装"; exit 1; }
export JAVA_HOME
echo ">>> 使用 JDK: ${JAVA_HOME}"

JVM_ARGS="-Dnacos.server-addr=124.221.150.239:9848 -Dnacos.namespace=prd -Dspring.cloud.nacos.discovery.register-enabled=false -Ddubbo.enabled=false -Dapp.gateway.trust-only=false"

echo ">>> 安装依赖模块 loan-api（首次需 install 供 loan-service 解析）"
mvn -pl loan-api -am install -DskipTests -q

echo ">>> 启动 ${MODULE}：直连 prd Nacos(124.221.150.239:9848) · namespace=prd · 不注册服务 · 关 Dubbo"
mvn -pl "${MODULE}" spring-boot:run -DskipTests -Dspring-boot.run.jvmArguments="${JVM_ARGS}"
