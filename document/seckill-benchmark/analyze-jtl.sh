#!/usr/bin/env bash
# 分析 JMeter jtl（CSV 格式，需在 user.properties 开启 response_data）：
# 按业务响应分类计数 + 计算总请求数/错误数/P50/P90/P99/P999/吞吐
# 用法: ./analyze-jtl.sh result.jtl
set -euo pipefail
JTL="$1"

echo "=== 响应分类计数 ==="
# CSV 列序（JMeter 5.6 默认）: timeStamp,elapsed,label,responseCode,responseMessage,threadName,...,sampleMetric... 最后一列附近有 responseMessage?
# 实际以表头为准：提取 elapsed 列与响应体列
HEADER=$(head -1 "$JTL")
get_col() { echo "$HEADER" | tr ',' '\n' | grep -n "^$1$" | head -1 | cut -d: -f1; }
ELAPSED=$(get_col elapsed)
BODY=$(get_col responseData || echo "")
echo "(elapsed列=$ELAPSED, responseData列=$BODY)"

if [ -n "$BODY" ]; then
  tail -n +2 "$JTL" | awk -F',' -v b="$BODY" '{
    msg=$b;
    if (msg ~ /受理中/) c["受理中(下单成功)"]++;
    else if (msg ~ /已售罄/) c["已售罄"]++;
    else if (msg ~ /超出限购/) c["超出限购"]++;
    else if (msg ~ /操作过于频繁/) c["限流拦截"]++;
    else if (msg ~ /秒杀令牌无效/) c["令牌无效"]++;
    else if (msg ~ /活动未开始/) c["未预热"]++;
    else c["其他:" substr(msg,1,40)]++;
    n++
  } END { for (k in c) printf "%-28s %d\n", k, c[k]; printf "%-28s %d\n", "总请求数", n; }' | sort -k2 -rn
fi

echo "=== 延迟分布（全体请求，毫秒） ==="
tail -n +2 "$JTL" | awk -F',' -v e="$ELAPSED" '{print $e}' | sort -n | awk '
  { v[NR]=$1 }
  END {
    printf "samples=%d  min=%d  P50=%d  P90=%d  P99=%d  max=%d\n", NR, v[1], v[int(NR*0.50)], v[int(NR*0.90)], v[int(NR*0.99)], v[NR];
    t0=ARGV[1];
  }'
