#!/usr/bin/env bash
# 发布 loan-platform/nacos/config/{dev|prd}/application.properties 到 Nacos
# loan 独立 group=loan（与 tse group=tse 隔离，同实例共存）
# 用法:
#   NACOS_SERVER_ADDR=127.0.0.1:8848 ./import-config.sh dev
#   NACOS_SERVER_ADDR=124.221.150.239:9848 ./import-config.sh prd
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NACOS_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:8848}"
GROUP="${NACOS_GROUP:-loan}"
NS="${1:-}"

if [[ -z "$NS" || ( "$NS" != "dev" && "$NS" != "prd" ) ]]; then
  echo "用法: NACOS_SERVER_ADDR=127.0.0.1:8848 $0 dev|prd"
  echo "仅支持 namespace: dev（本地 Docker）、prd（生产）"
  exit 1
fi

CFG_DIR="${ROOT}/config/${NS}"
if [[ ! -d "$CFG_DIR" ]]; then
  echo "目录不存在: $CFG_DIR"
  exit 1
fi

BASE="http://${NACOS_ADDR}/nacos/v1/cs/configs"

# 确保命名空间存在（dev/prd）
NS_ID=""
for ns in dev prd; do
  EXISTS=$(curl -sf --connect-timeout 10 "${BASE//cs\/configs/console\/namespaces}" 2>/dev/null \
    | grep -o "\"namespace\":\"${ns}\"" || true)
  if [[ -z "$EXISTS" ]]; then
    echo "注意: 命名空间 ${ns} 不存在，请在 Nacos 控制台创建（或运行 tse 的 ensure-namespaces.sh）" >&2
  fi
done

# 发布后端配置（group=loan）
DATA_ID="application.properties"
FILE="${CFG_DIR}/${DATA_ID}"
if [[ -f "$FILE" ]]; then
  echo "发布 ${NS}/application.properties (group=${GROUP}) → ${NACOS_ADDR}"
  curl -sf -X POST "${BASE}" \
    --data-urlencode "dataId=${DATA_ID}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "tenant=${NS}" \
    --data-urlencode "content@${FILE}" \
    --data-urlencode "type=properties" \
    -w "  HTTP %{http_code}\n" || echo "  ❌ 发布失败（检查 Nacos 地址/网络）"
else
  echo "跳过: ${FILE} 不存在"
fi

echo "完成。控制台核对: http://${NACOS_ADDR}/nacos → 命名空间 ${NS} → group ${GROUP}"
