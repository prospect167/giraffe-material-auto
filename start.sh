#!/bin/bash

# Giraffe Material Auto 启动脚本

echo "======================================"
echo "Giraffe Material Auto - 启动中..."
echo "======================================"

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java 环境，请先安装 JDK 1.8+"
    exit 1
fi

# 显示 Java 版本
echo "Java 版本:"
java -version

# 检查是否已编译
if [ ! -f "target/giraffe-material-auto-1.0.0-SNAPSHOT.jar" ]; then
    echo ""
    echo "未找到编译后的 jar 文件，开始编译..."
    echo ""
    mvn clean package -DskipTests
    
    if [ $? -ne 0 ]; then
        echo "编译失败，请检查错误信息"
        exit 1
    fi
fi

echo ""
echo "启动服务..."
echo ""

# 启动应用
java -jar target/giraffe-material-auto-1.0.0-SNAPSHOT.jar

exit 0

