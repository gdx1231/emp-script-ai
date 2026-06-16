#!/bin/bash
# 从数据库 AI_PROVIDER 表读取配置，生成 ai_settings.json
# 用法: source scripts/refresh_ai_settings.sh

DB_HOST="127.0.0.1"
DB_USER="sa"
DB_PASS="luke606,123"
DB_NAME="oneworld_main_data"
OUTPUT="src/test/resources/ai_settings.json"

SQLCMD="sqlcmd -S $DB_HOST -U $DB_USER -P '$DB_PASS' -d $DB_NAME -W -s'|'"

# 查询启用的 provider
PROVIDERS=$($SQLCMD -Q "select AP_CODE from AI_PROVIDER where AP_STATUS='USED' order by AP_CODE" 2>/dev/null | tail -n +3 | grep -v "rows affected")

echo "{" > "$OUTPUT"
FIRST=true

while IFS= read -r code; do
    [ -z "$code" ] && continue
    
    # 查询 URL 和 Key
    URL_LINE=$($SQLCMD -Q "select top 1 APU_URL, APU_KEY from AI_PROVIDER_URL where AP_CODE='$code' and APU_STATUS='USED' order by APU_MDATE desc" 2>/dev/null | tail -n +3 | head -1)
    API_URL=$(echo "$URL_LINE" | cut -d'|' -f1)
    API_KEY=$(echo "$URL_LINE" | cut -d'|' -f2)
    
    # 查询模型
    MODEL=$($SQLCMD -Q "select top 1 APM_CODE from AI_PROVIDER_MODEL where AP_CODE='$code' order by APM_CODE" 2>/dev/null | tail -n +3 | head -1 | tr -d ' ')
    
    [ -z "$API_URL" ] && API_URL=""
    [ -z "$API_KEY" ] && API_KEY=""
    [ -z "$MODEL" ] && MODEL=""
    
    if [ "$FIRST" = true ]; then
        FIRST=false
    else
        echo "," >> "$OUTPUT"
    fi
    
    cat >> "$OUTPUT" << EOF
  "$code": {
    "api_url": "$API_URL",
    "api_key": "$API_KEY",
    "model": "$MODEL"
  }
EOF
    
    printf "  %-20s %s | %s | %s\n" "$code" "✅" "${API_KEY:0:4}****${API_KEY: -4}" "$MODEL"
    
done <<< "$PROVIDERS"

echo "}" >> "$OUTPUT"

echo ""
echo "已生成 $OUTPUT"
