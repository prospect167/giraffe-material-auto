@echo off
chcp 65001 >nul
REM Giraffe Material Auto 启动脚本 (Windows)

echo ======================================
echo Giraffe Material Auto - 启动中...
echo ======================================

REM 检查 Java 环境
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到 Java 环境，请先安装 JDK 1.8+
    pause
    exit /b 1
)

REM 显示 Java 版本
echo Java 版本:
java -version

REM 检查是否已编译
if not exist "target\giraffe-material-auto-1.0.0-SNAPSHOT.jar" (
    echo.
    echo 未找到编译后的 jar 文件，开始编译...
    echo.
    call mvn clean package -DskipTests
    
    if %ERRORLEVEL% NEQ 0 (
        echo 编译失败，请检查错误信息
        pause
        exit /b 1
    )
)

echo.
echo 启动服务...
echo.

REM 启动应用
java -jar target\giraffe-material-auto-1.0.0-SNAPSHOT.jar

pause

