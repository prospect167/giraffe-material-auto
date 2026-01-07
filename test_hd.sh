#!/bin/bash

# 高清图下载优化测试脚本
# 使用方法: chmod +x test_hd.sh && ./test_hd.sh

echo "============================================"
echo "   高清图下载优化测试脚本"
echo "============================================"
echo ""

# 检查服务是否运行
echo "🔍 检查服务状态..."
if curl -s http://localhost:8080/api/v1/download/health > /dev/null 2>&1; then
    echo "✅ 服务运行正常"
else
    echo "❌ 服务未运行，请先启动服务:"
    echo "   mvn spring-boot:run"
    exit 1
fi

echo ""
echo "============================================"
echo "测试 1: 下载豆瓣电影剧照（高清）"
echo "============================================"

# 测试下载
curl -s -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://movie.douban.com/subject/1292052/photos?type=S&start=0",
    "targetDir": "test_hd_douban",
    "convertToJpeg": false
  }' | python3 -m json.tool 2>/dev/null || echo "下载请求已发送，请查看服务日志"

echo ""
echo "⏳ 等待下载完成（约 30-60 秒）..."
sleep 10

echo ""
echo "============================================"
echo "测试 2: 检查下载结果"
echo "============================================"

# 查找最新的下载目录
DOWNLOAD_BASE="downloads/test_hd_douban"
if [ -d "$DOWNLOAD_BASE" ]; then
    LATEST_DIR=$(find "$DOWNLOAD_BASE" -type d -name "2026*" 2>/dev/null | sort -r | head -1)
    
    if [ -d "$LATEST_DIR" ]; then
        echo "📁 下载目录: $LATEST_DIR"
        echo ""
        
        # 统计文件数量
        FILE_COUNT=$(ls "$LATEST_DIR"/*.jpg 2>/dev/null | wc -l | tr -d ' ')
        echo "📊 下载图片数量: $FILE_COUNT 张"
        echo ""
        
        if [ "$FILE_COUNT" -gt 0 ]; then
            # 显示前 5 个文件的大小
            echo "📏 图片文件大小（前5张）:"
            ls -lh "$LATEST_DIR"/*.jpg 2>/dev/null | head -5 | awk '{print "   " $9 " - " $5}'
            echo ""
            
            # 计算平均文件大小
            TOTAL_SIZE=$(du -sk "$LATEST_DIR" | awk '{print $1}')
            AVG_SIZE=$((TOTAL_SIZE / FILE_COUNT))
            echo "📈 平均文件大小: ${AVG_SIZE} KB"
            echo ""
            
            # 检查第一张图片的分辨率
            FIRST_IMAGE=$(ls "$LATEST_DIR"/*.jpg 2>/dev/null | head -1)
            if [ -f "$FIRST_IMAGE" ]; then
                echo "🖼️  样例图片分辨率:"
                if command -v sips &> /dev/null; then
                    WIDTH=$(sips -g pixelWidth "$FIRST_IMAGE" 2>/dev/null | tail -1 | awk '{print $2}')
                    HEIGHT=$(sips -g pixelHeight "$FIRST_IMAGE" 2>/dev/null | tail -1 | awk '{print $2}')
                    echo "   ${WIDTH} × ${HEIGHT}"
                else
                    file "$FIRST_IMAGE" | grep -o '[0-9]* x [0-9]*' || echo "   (无法获取，请安装 sips 或 ImageMagick)"
                fi
            fi
            echo ""
            
            # 判断是否为高清图
            echo "============================================"
            echo "✅ 验证结果"
            echo "============================================"
            
            if [ "$AVG_SIZE" -gt 500 ]; then
                echo "✅ 文件大小: ${AVG_SIZE} KB > 500 KB (高清)"
            else
                echo "❌ 文件大小: ${AVG_SIZE} KB < 500 KB (可能是低清)"
            fi
            
            if command -v sips &> /dev/null && [ -n "$WIDTH" ] && [ "$WIDTH" -gt 1000 ]; then
                echo "✅ 图片分辨率: ${WIDTH} × ${HEIGHT} (高清)"
            elif [ -n "$WIDTH" ]; then
                echo "❌ 图片分辨率: ${WIDTH} × ${HEIGHT} (可能是低清)"
            else
                echo "⚠️  无法检测分辨率"
            fi
            
            echo ""
            echo "💡 提示: 请查看服务日志，确认是否有以下关键词:"
            echo "   - '升级图片URL为高清'"
            echo "   - '提取到高清图片'"
            echo "   - '/raw/' (豆瓣高清标识)"
            
        else
            echo "❌ 未找到下载的图片文件"
        fi
    else
        echo "❌ 未找到下载目录"
    fi
else
    echo "❌ 下载基础目录不存在: $DOWNLOAD_BASE"
fi

echo ""
echo "============================================"
echo "测试 3: 查看最近的日志"
echo "============================================"

if [ -f "logs/giraffe-material-auto.log" ]; then
    echo "📋 最近的关键日志（最后 20 行）:"
    tail -20 logs/giraffe-material-auto.log | grep -E "升级|高清|raw|提取到|成功|失败" || echo "   (未找到相关日志)"
else
    echo "⚠️  日志文件不存在，请检查日志配置"
fi

echo ""
echo "============================================"
echo "🎉 测试完成！"
echo "============================================"
echo ""
echo "📚 查看详细文档:"
echo "   - 高清图片下载优化说明.md"
echo "   - 快速验证高清图优化.md"
echo "   - 优化总结.md"
echo ""
echo "❓ 如有问题，请查看文档或联系开发团队"
echo ""

