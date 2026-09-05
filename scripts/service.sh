#!/usr/bin/env bash
# ============================================================
# loan 四端服务管理脚本：后端(8080) / 网关(8088) / Web(5173) / 小程序 H5(5174)
#
# 用法:
#   bash scripts/service.sh start   [all|backend|gateway|web|mini]
#   bash scripts/service.sh stop    [all|backend|gateway|web|mini]
#   bash scripts/service.sh restart [all|backend|gateway|web|mini]
#   bash scripts/service.sh status
#
# 关键设计（解决"服务总是自动停掉"）:
#   1. launchctl submit —— 交给 macOS 用户级服务管理，调用会话结束后进程仍存活
#   2. 日志 —— mini 写入 logs/；backend/gateway/Web 受 macOS Downloads 后台权限限制写入 /tmp
#   3. 停止 —— 先移除对应用户级服务，再清理监听端口的进程
#   4. 幂等 —— 端口已监听则跳过启动，重复执行不产生多实例
# ============================================================
set -u
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${BASE_DIR}/logs"
mkdir -p "$LOG_DIR"

# JDK 探测由公共工具统一管理：必须是 Java 8，Apple 芯片优先原生 arm64。
# 可通过 LOAN_JAVA_HOME 显式覆盖，但不接受 JDK 17 等非 Java 8 版本。
source "$BASE_DIR/scripts/lib/java8.sh"
JAVA_HOME="$(loan_detect_java8)" || exit 1
# 路径可用环境变量覆盖以适配不同机器（LOAN_MVN / LOAN_NODE / LOAN_NPM），默认保留原路径。
MVN="${LOAN_MVN:-/Users/admin/Documents/developer/apache-maven-3.8.8/bin/mvn}"
NODE="${LOAN_NODE:-/Users/admin/.workbuddy/binaries/node/versions/22.22.2/bin/node}"
NPM="${LOAN_NPM:-/Users/admin/.workbuddy/binaries/node/versions/22.22.2/bin/npm}"
GATEWAY_RUN_JAR="/tmp/loan-gateway-dev.jar"
GATEWAY_RUN_LOG="/tmp/loan-gateway-dev.log"
BACKEND_RUN_JAR="/tmp/loan-service-dev.jar"
BACKEND_RUN_LOG="/tmp/loan-service-dev.log"
RUNTIME_DIR="/tmp/loan-runtime"
BACKEND_LOG_DIR="${RUNTIME_DIR}/logs"
WEB_RUN_LOG="/tmp/loan-web-dev.log"
WATCHDOG_RUN_LOG="/tmp/loan-watchdog-dev.log"
mkdir -p "$BACKEND_LOG_DIR"

# macOS 用户级服务标签（仅当前登录用户，不安装系统守护项）
LABEL_PREFIX="com.loan.dev"

# 保活实现：macOS 10.15+ 起 `launchctl submit` 已被废弃（在 macOS 15 上静默空操作，
# 既不注册标签也不产生日志），故改用 LaunchAgent plist + `launchctl bootstrap` 注册到
# 当前登录用户 gui 域：调用会话结束后进程仍存活，与原先 submit 的语义保持一致。
LAUNCH_AGENTS_DIR="${HOME}/Library/LaunchAgents"

launch_job() { # $1=label $2=log $3...=command
  local label="$1"
  local log_file="$2"
  shift 2
  local plist="${LAUNCH_AGENTS_DIR}/${label}.plist"
  remove_job "$label"
  mkdir -p "$LAUNCH_AGENTS_DIR"
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
    printf '<plist version="1.0"><dict>\n'
    printf '  <key>Label</key><string>%s</string>\n' "$label"
    printf '  <key>ProgramArguments</key><array>\n'
    local arg
    for arg in "$@"; do
      printf '    <string>%s</string>\n' "$arg"
    done
    printf '  </array>\n'
    printf '  <key>StandardOutPath</key><string>%s</string>\n' "$log_file"
    printf '  <key>StandardErrorPath</key><string>%s</string>\n' "$log_file"
    printf '  <key>RunAtLoad</key><true/>\n'
    printf '  <key>KeepAlive</key><false/>\n'
    printf '</dict></plist>\n'
  } > "$plist"
  if ! launchctl bootstrap "gui/${UID}" "$plist" 2>/dev/null; then
    # 已注册过同名服务时先注销再重试
    launchctl bootout "gui/${UID}/${label}" >/dev/null 2>&1 || true
    launchctl bootstrap "gui/${UID}" "$plist"
  fi
}

remove_job() { # $1=label
  launchctl bootout "gui/${UID}/$1" >/dev/null 2>&1 || true
  rm -f "${LAUNCH_AGENTS_DIR}/$1.plist"
}

# 端口探测：curl 同时支持 IPv4/IPv6（vite 只监听 ::1 时 nc 127.0.0.1 会误判）
port_up() {
  curl -s -o /dev/null -m 2 "http://localhost:$1/" && return 0
  nc -z -w 2 127.0.0.1 "$1" 2>/dev/null
}

start_backend() {
  if port_up 8080; then echo "[backend] 已在运行 (8080)"; return; fi
  echo "[backend] 构建可运行包..."
  (cd "$BASE_DIR" && JAVA_HOME="$JAVA_HOME" "$MVN" -q -pl loan-service -am package -DskipTests) || {
    echo "[backend] 构建失败，请检查 Maven 输出"
    return 1
  }
  cp "$BASE_DIR/loan-service/target/loan-service-1.0.0.jar" "$BACKEND_RUN_JAR"
  # launchctl submit 不支持 WorkingDirectory：由 shell 先切换到固定运行目录，再 exec Java。
  # Java 仅携带约定的 5 个 -D 参数；Log4j2 通过环境变量使用绝对日志目录。
  launch_job "$LABEL_PREFIX.backend" "$BACKEND_RUN_LOG" \
    /usr/bin/env "JAVA_HOME=$JAVA_HOME" "LOAN_LOG_DIR=$BACKEND_LOG_DIR" /bin/bash -lc \
    "cd '$RUNTIME_DIR' && exec '$JAVA_HOME/bin/java' \
      -Dnacos.server-addr=124.221.150.239:9848 \
      -Dnacos.namespace=prd \
      -Dspring.cloud.nacos.discovery.register-enabled=false \
      -Ddubbo.enabled=false \
      -Dapp.gateway.trust-only=false \
      -jar '$BACKEND_RUN_JAR'"
  echo "[backend] 启动中 (工作目录: $RUNTIME_DIR; 日志目录: $BACKEND_LOG_DIR)"
}

start_gateway() {
  if port_up 8088; then echo "[gateway] 已在运行 (8088)"; return; fi
  echo "[gateway] 构建可运行包..."
  (cd "$BASE_DIR" && JAVA_HOME="$JAVA_HOME" "$MVN" -q -pl loan-gateway -am package -DskipTests) || {
    echo "[gateway] 构建失败，请检查 Maven 输出"
    return 1
  }
  # macOS 用户级后台进程不能直接读取 Downloads 下的大型 JAR，复制到临时运行目录。
  cp "$BASE_DIR/loan-gateway/target/loan-gateway-1.0.0.jar" "$GATEWAY_RUN_JAR"
  launch_job "$LABEL_PREFIX.gateway" "$GATEWAY_RUN_LOG" \
    /usr/bin/env "JAVA_HOME=$JAVA_HOME" "$JAVA_HOME/bin/java" \
    -jar "$GATEWAY_RUN_JAR" \
    --server.port=8088 \
    --spring.redis.host=124.221.116.28 \
    --spring.redis.port=9379 \
    --spring.redis.password="${LOAN_REDIS_PASSWORD:-CHANGE_ME_REDIS}" \
    --jwt.secret="${LOAN_JWT_SECRET:-CHANGE_ME_JWT_SECRET}"
  echo "[gateway] 启动中 (日志: $GATEWAY_RUN_LOG)"
}

start_web() {
  if port_up 5173; then echo "[web] 已在运行 (5173)"; return; fi
  launch_job "$LABEL_PREFIX.web" "$WEB_RUN_LOG" /bin/bash -lc \
    "cd '$BASE_DIR/loan-web' && '$NPM' run dev -- --port 5173 --strictPort"
  echo "[web] 启动中 (日志: $WEB_RUN_LOG)"
}

start_mini() {
  if port_up 5174; then echo "[mini] 已在运行 (5174)"; return; fi
  launch_job "$LABEL_PREFIX.mini" "$LOG_DIR/mini.log" /bin/bash -lc \
    "cd '$BASE_DIR/loan-mini' && '$NPM' run dev:h5 -- --port 5174 --strictPort"
  echo "[mini] 启动中 (日志: logs/mini.log)"
}

stop_port() { # $1=port
  local pids
  pids=$(lsof -ti ":$1" 2>/dev/null)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill -9 2>/dev/null
    sleep 1
  fi
}

stop_backend()  { remove_job "$LABEL_PREFIX.backend"; stop_port 8080; echo "[backend] 已停止"; }
stop_gateway()  { remove_job "$LABEL_PREFIX.gateway"; stop_port 8088; echo "[gateway] 已停止"; }
stop_web()      { remove_job "$LABEL_PREFIX.web"; stop_port 5173; echo "[web] 已停止"; }
stop_mini()     { remove_job "$LABEL_PREFIX.mini"; stop_port 5174; echo "[mini] 已停止"; }

status() {
  loan_print_java8_summary "$JAVA_HOME"
  for item in "8080 backend" "8088 gateway" "5173 web" "5174 mini"; do
    set -- $item
    if port_up "$1"; then echo "[$2] 运行中 ($1)"; else echo "[$2] 已停止 ($1)"; fi
  done
}

# 守护循环：每 30 秒检查四端，发现端口未监听则自动拉起（进程被杀也能自愈）
start_watchdog() {
  if launchctl list | grep -q "$LABEL_PREFIX.watchdog"; then
    echo "[watchdog] 已在运行"
    return
  fi
  launch_job "$LABEL_PREFIX.watchdog" "$WATCHDOG_RUN_LOG" /bin/bash -lc "
    while true; do
      for entry in '8080 backend' '8088 gateway' '5173 web' '5174 mini'; do
        set -- \$entry
        if ! curl -s -o /dev/null -m 2 'http://localhost:'\$1'/' 2>/dev/null && ! nc -z -w 2 127.0.0.1 \$1 2>/dev/null; then
          echo \"[\$(date '+%H:%M:%S')] 检测到 \$2(port \$1) 停止，自动拉起\"
          bash '$BASE_DIR/scripts/service.sh' start \$2 >/dev/null 2>&1
        fi
      done
      sleep 30
    done
  "
  echo "[watchdog] 守护已启动 (30s 巡检，自动拉起; 日志: $WATCHDOG_RUN_LOG)"
}

stop_watchdog() {
  remove_job "$LABEL_PREFIX.watchdog"
  echo "[watchdog] 已停止"
}

CMD="${1:-status}"
TARGET="${2:-all}"

case "$CMD" in
  start)
    case "$TARGET" in
      backend) start_backend ;;
      gateway) start_gateway ;;
      web)     start_web ;;
      mini)    start_mini ;;
      all)     start_backend; start_gateway; start_web; start_mini ;;
      *) echo "未知目标: $TARGET (backend|gateway|web|mini|all)"; exit 1 ;;
    esac
    ;;
  stop)
    case "$TARGET" in
      backend) stop_backend ;;
      gateway) stop_gateway ;;
      web)     stop_web ;;
      mini)    stop_mini ;;
      all)     stop_backend; stop_gateway; stop_web; stop_mini ;;
      *) echo "未知目标: $TARGET (backend|gateway|web|mini|all)"; exit 1 ;;
    esac
    ;;
  watchdog)
    case "$TARGET" in
      start) start_watchdog ;;
      stop)  stop_watchdog ;;
      *) echo "用法: bash scripts/service.sh watchdog {start|stop}"; exit 1 ;;
    esac
    ;;
  restart)
    bash "$0" stop "$TARGET"; sleep 2; bash "$0" start "$TARGET"
    ;;
  status) status ;;
  *) echo "用法: bash scripts/service.sh {start|stop|restart|status|watchdog} [all|backend|gateway|web|mini]"; exit 1 ;;
esac
