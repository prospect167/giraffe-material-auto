#!/bin/bash

# 豆瓣高清图片下载优化测试脚本
# 测试 URL: https://movie.douban.com/photos/photo/2929311086/

echo "=========================================="
echo "豆瓣高清图片下载优化测试"
echo "=========================================="
echo ""

# 测试配置
API_URL="http://localhost:8080/api/v1/download/images"
TEST_URL="https://movie.douban.com/photos/photo/2929311086/"
TARGET_DIR="test_douban_hd_$(date +%Y%m%d_%H%M%S)"

echo "测试 URL: $TEST_URL"
echo "目标目录: $TARGET_DIR"
echo ""

# 发送下载请求
echo "正在发送下载请求..."
RESPONSE=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d "{
    \"url\": \"$TEST_URL\",
    \"targetDir\": \"$TARGET_DIR\",
    \"useTimestamp\": false
  }")

echo "响应结果:"
echo "$RESPONSE" | jq '.'

# 解析结果
SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
TOTAL_COUNT=$(echo "$RESPONSE" | jq -r '.totalCount')
SUCCESS_COUNT=$(echo "$RESPONSE" | jq -r '.successCount')
SAVE_PATH=$(echo "$RESPONSE" | jq -r '.savePath')

echo ""
echo "=========================================="
echo "下载结果统计"
echo "=========================================="
echo "成功状态: $SUCCESS"
echo "图片总数: $TOTAL_COUNT"
echo "成功数量: $SUCCESS_COUNT"
echo "保存路径: $SAVE_PATH"
echo ""

# 检查下载的图片
if [ "$SUCCESS" == "true" ] && [ -d "$SAVE_PATH" ]; then
    echo "=========================================="
    echo "图片质量检查"
    echo "=========================================="
    
    for img in "$SAVE_PATH"/*.{jpg,jpeg,png,webp} 2>/dev/null; do
        if [ -f "$img" ]; then
            FILE_SIZE=$(ls -lh "$img" | awk '{print $5}')
            echo ""
            echo "文件: $(basename "$img")"
            echo "大小: $FILE_SIZE"
            
            # 尝试获取图片尺寸（macOS使用sips，Linux可以用identify）
            if command -v sips &> /dev/null; then
                WIDTH=$(sips -g pixelWidth "$img" 2>/dev/null | tail -1 | awk '{print $2}')
                HEIGHT=$(sips -g pixelHeight "$img" 2>/dev/null | tail -1 | awk '{print $2}')
                if [ ! -z "$WIDTH" ] && [ ! -z "$HEIGHT" ]; then
                    echo "尺寸: ${WIDTH}x${HEIGHT}"
                    
                    # 判断清晰度（期望 1280x2276 或接近）
                    if [ "$WIDTH" -ge 1200 ] || [ "$HEIGHT" -ge 2000 ]; then
                        echo "✅ 高清图片（达到期望清晰度）"
                    elif [ "$WIDTH" -ge 1000 ] && [ "$HEIGHT" -ge 1800 ]; then
                        echo "⚠️  中等清晰度（接近但未达到最高清晰度）"
                    else
                        echo "❌ 低清晰度（未达到期望）"
                    fi
                fi
            elif command -v identify &> /dev/null; then
                DIMENSIONS=$(identify -format "%wx%h" "$img" 2>/dev/null)
                echo "尺寸: $DIMENSIONS"
            fi
        fi
    done
    
    echo ""
    echo "=========================================="
    echo "测试完成"
    echo "=========================================="
    echo "请手动验证图片清晰度是否符合要求"
    echo "参考标准: 1280x2276 像素"
else
    echo "❌ 下载失败或未找到下载的图片"
fi

echo ""

