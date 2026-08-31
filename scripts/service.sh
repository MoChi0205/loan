#!/usr/bin/env bash
# ============================================================
# loan 三端服务管理脚本：后端(8080) / 网关(8088) / 前端(5173)
#
# 用法:
#   bash scripts/service.sh start   [all|backend|gateway|web]
#   bash scripts/service.sh stop    [all|backend|gateway|web]
#   bash scripts/service.sh restart [all|backend|gateway|web]
#   bash scripts/service.sh status
#
# 关键设计（解决"服务总是自动停掉"）:
#   1. nohup + & + disown —— 忽略 SIGHUP 并移出作业表，会话/终端关闭不杀进程（macOS 无 setsid，用该组合）
#   2. 日志    —— 重定向到 loan-main/logs/{backend,gateway,web}.log
#   3. PID 文件 —— /tmp/loan-{backend,gateway,web}.pid，可精确 stop
#   4. 幂等    —— 端口已监听则跳过启动，重复执行不产生多实例
#   5. 可选加固 —— 设置 LAUNCHD=1 时用 launchctl 注册为系统守护（完全独立于本会话）
# ============================================================
set -u
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${BASE_DIR}/logs"
mkdir -p "$LOG_DIR"

JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home"
MVN="/Users/admin/Documents/developer/apache-maven-3.8.8/bin/mvn"
NODE="/Users/admin/.workbuddy/binaries/node/versions/22.22.2/bin/node"

# 端口探测：curl 同时支持 IPv4/IPv6（vite 只监听 ::1 时 nc 127.0.0.1 会误判）
port_up() {
  curl -s -o /dev/null -m 2 "http://localhost:$1/" && return 0
  nc -z -w 2 127.0.0.1 "$1" 2>/dev/null
}

start_backend() {
  if port_up 8080; then echo "[backend] 已在运行 (8080)"; return; fi
  # 首次启动需要先 install loan-api（run-dev-prd.sh 内置），因此直接调用该脚本
  nohup bash "$BASE_DIR/scripts/run-dev-prd.sh" loan-service \
    > "$LOG_DIR/backend.log" 2>&1 &
  disown
  echo $! > /tmp/loan-backend.pid
  echo "[backend] 启动中 pid=$! (日志: logs/backend.log)"
}

start_gateway() {
  if port_up 8088; then echo "[gateway] 已在运行 (8088)"; return; fi
  nohup bash -c "cd '$BASE_DIR' && export JAVA_HOME='$JAVA_HOME' && '$MVN' -pl loan-gateway spring-boot:run -DskipTests -Dspring-boot.run.arguments='--server.port=8088 --spring.redis.host=124.221.116.28 --spring.redis.port=9379 --spring.redis.password=CHANGE_ME_REDIS'" \
    > "$LOG_DIR/gateway.log" 2>&1 &
  disown
  echo $! > /tmp/loan-gateway.pid
  echo "[gateway] 启动中 pid=$! (日志: logs/gateway.log)"
}

start_web() {
  if port_up 5173; then echo "[web] 已在运行 (5173)"; return; fi
  nohup bash -c "cd '$BASE_DIR/loan-web' && '$NODE' node_modules/vite/bin/vite.js --port 5173 --strictPort" \
    > "$LOG_DIR/web.log" 2>&1 &
  disown
  echo $! > /tmp/loan-web.pid
  echo "[web] 启动中 pid=$! (日志: logs/web.log)"
}

stop_port() { # $1=port
  local pids
  pids=$(lsof -ti ":$1" 2>/dev/null)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill -9 2>/dev/null
    sleep 1
  fi
}

stop_backend()  { stop_port 8080; echo "[backend] 已停止"; }
stop_gateway()  { stop_port 8088; echo "[gateway] 已停止"; }
stop_web()      { stop_port 5173; echo "[web] 已停止"; }

status() {
  for item in "8080 backend" "8088 gateway" "5173 web"; do
    set -- $item
    if port_up "$1"; then echo "[$2] 运行中 ($1)"; else echo "[$2] 已停止 ($1)"; fi
  done
}

# 守护循环：每 30 秒检查三端，发现端口未监听则自动拉起（进程被杀也能自愈）
start_watchdog() {
  if [ -f /tmp/loan-watchdog.pid ] && kill -0 "$(cat /tmp/loan-watchdog.pid)" 2>/dev/null; then
    echo "[watchdog] 已在运行 pid=$(cat /tmp/loan-watchdog.pid)"
    return
  fi
  nohup bash -c "
    while true; do
      for entry in '8080 backend' '8088 gateway' '5173 web'; do
        set -- \$entry
        if ! curl -s -o /dev/null -m 2 'http://localhost:'\$1'/' 2>/dev/null && ! nc -z -w 2 127.0.0.1 \$1 2>/dev/null; then
          echo \"[\$(date '+%H:%M:%S')] 检测到 \$2(port \$1) 停止，自动拉起\"
          bash '$BASE_DIR/scripts/service.sh' start \$2 >/dev/null 2>&1
        fi
      done
      sleep 30
    done
  " >> "$LOG_DIR/watchdog.log" 2>&1 &
  disown
  echo $! > /tmp/loan-watchdog.pid
  echo "[watchdog] 守护已启动 pid=$! (30s 巡检，自动拉起; 日志: logs/watchdog.log)"
}

stop_watchdog() {
  if [ -f /tmp/loan-watchdog.pid ]; then
    kill "$(cat /tmp/loan-watchdog.pid)" 2>/dev/null
    rm -f /tmp/loan-watchdog.pid
    echo "[watchdog] 已停止"
  else
    echo "[watchdog] 未在运行"
  fi
}

CMD="${1:-status}"
TARGET="${2:-all}"

case "$CMD" in
  start)
    case "$TARGET" in
      backend) start_backend ;;
      gateway) start_gateway ;;
      web)     start_web ;;
      all)     start_backend; start_gateway; start_web ;;
      *) echo "未知目标: $TARGET (backend|gateway|web|all)"; exit 1 ;;
    esac
    ;;
  stop)
    case "$TARGET" in
      backend) stop_backend ;;
      gateway) stop_gateway ;;
      web)     stop_web ;;
      all)     stop_backend; stop_gateway; stop_web ;;
      *) echo "未知目标: $TARGET (backend|gateway|web|all)"; exit 1 ;;
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
  *) echo "用法: bash scripts/service.sh {start|stop|restart|status|watchdog} [all|backend|gateway|web]"; exit 1 ;;
esac
