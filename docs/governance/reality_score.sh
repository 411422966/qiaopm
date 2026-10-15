#!/bin/bash
# Reality Score 自动评分脚本
# 用法：bash docs/governance/reality_score.sh
# 输出：JSON 至 docs/evidence/reality/reality_score_$(date +%Y%m%d_%H%M%S).json
# 严禁人工修改分数，必须基于代码扫描结果自动计算

set -e

BACKEND="/d/QiaoPM/backend/src/main/java/com/meiyetec"
FRONTEND="/d/QiaoPM/frontend/meiyetec-web"
DOC="/d/QiaoPM/docs/audit/FEATURE_COMPARISON_MATRIX.md"
INVENTORY="/d/QiaoPM/docs/governance/feature_inventory.tsv"
OUT_DIR="/d/QiaoPM/docs/evidence/reality"
mkdir -p "$OUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT="$OUT_DIR/reality_score_${TIMESTAMP}.json"

# 跳过 TSV 注释行（以 # 开头）
count_code_files() {
  local pattern=$1
  # Controller + Service + Mapper + Entity + 至少一个 SQL migration
  local ctrl=$(find "$BACKEND/controller" -name "${pattern}Controller.java" 2>/dev/null | wc -l)
  local svc=$(find "$BACKEND/service" -name "${pattern}Service.java" 2>/dev/null | wc -l)
  local mapper=$(find "$BACKEND/mapper" -name "${pattern}Mapper.java" 2>/dev/null | wc -l)
  local entity=$(find "$BACKEND/entity" -name "${pattern}.java" 2>/dev/null | wc -l)
  local sql=$(grep -ril "${pattern,,}" /d/QiaoPM/backend/src/main/resources/db/sql/ 2>/dev/null | wc -l)
  local total=$((ctrl + svc + mapper + entity))
  if [ "$ctrl" -ge 1 ] && [ "$svc" -ge 1 ] && [ "$total" -ge 3 ] && [ "$sql" -ge 1 ]; then
    echo "1.0"
  elif [ "$ctrl" -ge 1 ] && [ "$svc" -ge 1 ] && [ "$total" -ge 2 ]; then
    echo "0.5"
  elif [ "$svc" -ge 1 ] || [ "$ctrl" -ge 1 ]; then
    echo "0.2"
  else
    echo "0.0"
  fi
}

count_e2e_tests() {
  local spec=$1
  if [ -f "$FRONTEND/e2e/${spec}.spec.ts" ]; then
    local tests=$(grep -c "^\s*test(" "$FRONTEND/e2e/${spec}.spec.ts" 2>/dev/null | head -1)
    tests=${tests:-0}
    if [ "$tests" -ge 5 ]; then echo "1.0"
    elif [ "$tests" -ge 1 ]; then echo "0.5"
    else echo "0.0"
    fi
  else echo "0.0"
  fi
}

probe_url() {
  local url=$1
  local code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then echo "1.0"
  elif [ "$code" = "000" ]; then echo "N/A"
  else echo "0.0"
  fi
}

check_doc_sync() {
  local keyword=$1
  if grep -q "$keyword" "$DOC" 2>/dev/null; then echo "1.0"
  else echo "0.5"
  fi
}

# 计算单条功能得分（0.50*code + 0.25*e2e + 0.15*runtime + 0.10*doc）
calc_feature() {
  local code=$1 e2e=$2 runtime=$3 doc=$4
  # runtime=N/A 时仅用 code(0.55)+e2e(0.27)+doc(0.18) 归一化
  if [ "$runtime" = "N/A" ]; then
    awk -v c="$code" -v e="$e2e" -v d="$doc" \
      'BEGIN { printf "%.4f", 0.55*c + 0.27*e + 0.18*d }'
  else
    awk -v c="$code" -v e="$e2e" -v r="$runtime" -v d="$doc" \
      'BEGIN { printf "%.4f", 0.50*c + 0.25*e + 0.15*r + 0.10*d }'
  fi
}

# 输出 JSON
{
  echo "{"
  echo "  \"scored_at\": \"$(date -Iseconds)\","
  echo "  \"model\": \"0.50*code + 0.25*e2e + 0.15*runtime + 0.10*doc\","
  echo "  \"inventory\": \"$INVENTORY\","
  echo "  \"features\": ["

  local first=1
  while IFS=$'\t' read -r fid module ctrl svc spec url keyword; do
    [[ "$fid" =~ ^#.*$ ]] && continue
    [[ -z "$fid" ]] && continue
    # 从 Controller 类名抽取 Service 简写（去 Controller 后缀）
    local svcsimple="${svc%Controller}Service"
    [[ "$svcsimple" == "Service" ]] && svcsimple="${svc%Controller}"
    # 代码实装度扫描
    local code_score=$(count_code_files "$ctrl")
    # E2E 覆盖度扫描
    local e2e_score=$(count_e2e_tests "$spec")
    # 运行时探测
    local runtime_score="N/A"
    if [ -n "$url" ]; then
      runtime_score=$(probe_url "$url")
    fi
    # 文档同步
    local doc_score=$(check_doc_sync "$keyword")
    # 总分
    local total=$(calc_feature "$code_score" "$e2e_score" "$runtime_score" "$doc_score")

    if [ $first -eq 0 ]; then echo "    ,"; fi
    first=0
    printf "    {\n"
    printf "      \"id\": \"%s\",\n" "$fid"
    printf "      \"module\": \"%s\",\n" "$module"
    printf "      \"code\": %s,\n" "$code_score"
    printf "      \"e2e\": %s,\n" "$e2e_score"
    printf "      \"runtime\": \"%s\",\n" "$runtime_score"
    printf "      \"doc\": %s,\n" "$doc_score"
    printf "      \"score\": %s\n" "$total"
    printf "    }"
  done < "$INVENTORY"

  echo ""
  echo "  ]"
  echo "}"
} > "$OUT"

# 同时输出概要（终端可见）
echo "=========================================="
echo "Reality Score 计算完成"
echo "输出文件: $OUT"
echo "=========================================="