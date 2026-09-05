#!/usr/bin/env bash
# JDK 8 统一探测工具。调用方只需执行：JAVA_HOME="$(loan_detect_java8)"。

loan_java8_valid() {
  local java_home="$1"
  [ -n "$java_home" ] || return 1
  [ -x "$java_home/bin/java" ] || return 1
  "$java_home/bin/java" -version 2>&1 | grep -q 'version "1\.8\.'
}

loan_java_arch() {
  local java_bin="$1"
  file "$java_bin" 2>/dev/null | awk '
    /arm64|aarch64/ { print "arm64"; exit }
    /x86_64/ { print "x86_64"; exit }
  '
}

loan_native_java8() {
  local host_arch="$1"
  shift
  local java_home java_arch
  for java_home in "$@"; do
    loan_java8_valid "$java_home" || continue
    java_arch="$(loan_java_arch "$java_home/bin/java")"
    if [ -z "$java_arch" ] || [ "$java_arch" = "$host_arch" ]; then
      printf '%s\n' "$java_home"
      return 0
    fi
  done
  return 1
}

loan_detect_java8() {
  local host_arch registered_java selected java_home java_arch
  host_arch="$(uname -m)"
  [ "$host_arch" = "aarch64" ] && host_arch="arm64"

  # 显式覆盖优先，但仍强制校验 Java 8；架构不匹配时给出明确告警。
  if [ -n "${LOAN_JAVA_HOME:-}" ]; then
    if ! loan_java8_valid "$LOAN_JAVA_HOME"; then
      echo "ERROR: LOAN_JAVA_HOME 不是可用的 JDK 8: $LOAN_JAVA_HOME" >&2
      return 1
    fi
    java_arch="$(loan_java_arch "$LOAN_JAVA_HOME/bin/java")"
    if [ -n "$java_arch" ] && [ "$java_arch" != "$host_arch" ]; then
      echo "WARN: LOAN_JAVA_HOME 架构为 $java_arch，本机为 $host_arch，启动可能经过兼容层" >&2
    fi
    printf '%s\n' "$LOAN_JAVA_HOME"
    return 0
  fi

  registered_java="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
  # 原生架构优先，避免 Apple 芯片误选 x86_64 JDK 后经 Rosetta 慢启动。
  selected="$(loan_native_java8 "$host_arch" \
    "/Users/admin/Documents/developer/jdk8-arm64/Contents/Home" \
    "/Users/admin/Documents/developer/jdk1.8/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home" \
    "$registered_java" \
    "${JAVA_HOME:-}" || true)"
  if [ -n "$selected" ]; then
    printf '%s\n' "$selected"
    return 0
  fi

  # 没有原生版本时允许退回任意可执行 JDK 8，但明确说明兼容层风险。
  for java_home in \
    "/Users/admin/Documents/developer/jdk1.8/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home" \
    "$registered_java" \
    "${JAVA_HOME:-}"; do
    loan_java8_valid "$java_home" || continue
    java_arch="$(loan_java_arch "$java_home/bin/java")"
    echo "WARN: 未找到本机架构 JDK 8，退回 ${java_arch:-未知架构} 版本: $java_home" >&2
    printf '%s\n' "$java_home"
    return 0
  done

  echo "ERROR: 未找到可用的 JDK 8；Apple 芯片建议安装 arm64 JDK 8" >&2
  return 1
}

loan_print_java8_summary() {
  local java_home="$1"
  local java_arch java_version
  java_arch="$(loan_java_arch "$java_home/bin/java")"
  java_version="$($java_home/bin/java -version 2>&1 | head -1)"
  echo "[java] JAVA_HOME=$java_home"
  echo "[java] 架构=${java_arch:-未知}，版本=$java_version"
}
