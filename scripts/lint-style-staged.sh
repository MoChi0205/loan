#!/usr/bin/env bash
# ============================================================================
# lint-staged 门禁脚本：按文件所属前端子包，调用该子包自带的 stylelint --fix。
# 仅校验「暂存」文件，避免全量扫描阻塞提交；裸 hex / 未知单位等不可自动修复
# 的违规会使 stylelint 非零退出，从而阻断提交（真实门禁）。
#
# lint-staged 传入的参数 $@ 为相对仓库根的路径（如 loan-mini/pages/home/home.vue），
# 这里 cd 进对应子包并去掉前缀后交给子包自己的 stylelint 二进制。
# ============================================================================
set -e

for f in "$@"; do
  case "$f" in
    loan-mini/*)
      ( cd loan-mini && ./node_modules/.bin/stylelint --fix "${f#loan-mini/}" )
      ;;
    loan-web/*)
      ( cd loan-web && ./node_modules/.bin/stylelint --fix "${f#loan-web/}" )
      ;;
  esac
done
