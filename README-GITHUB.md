# Giraffe Material Auto - 素材自动化下载服务

<p align="center">
  <img src="https://img.shields.io/badge/Java-8-orange.svg" alt="Java 8">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.6.13-green.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
</p>

## 🎯 项目简介

一个强大的网页图片自动下载工具，支持智能翻页、批量下载和可选的AI去水印功能。

### ✨ 核心功能

- 🔍 **智能图片提取** - 支持多种方式提取图片（img标签、懒加载、背景图）
- 📄 **自动翻页爬取** - 自动识别分页，爬取所有页面的图片
- 🖼️ **格式转换** - 自动转换图片为 JPEG 格式
- 🤖 **AI 去水印** - 支持阿里云、腾讯云、百度云（可选）
- 🔄 **失败重试** - 可配置的重试机制
- 📊 **详细统计** - 完整的下载和处理统计信息
- 🚀 **RESTful API** - 标准的 HTTP 接口

## 🚀 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.x+

### 编译运行

```bash
# 克隆项目
git clone https://github.com/your-username/giraffe-material-auto.git
cd giraffe-material-auto

# 编译
mvn clean package -DskipTests

# 运行
java -jar target/giraffe-material-auto-1.0.0-SNAPSHOT.jar
```

或使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

## 📖 使用示例

### 基础下载

```bash
curl -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/photos",
    "targetDir": "my_photos",
    "convertToJpeg": true
  }'
```

### 自动翻页下载

```bash
curl -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/photos",
    "targetDir": "my_photos",
    "crawlAllPages": true,
    "maxPages": 50
  }'
```

### 使用 AI 去水印

```bash
curl -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/photos",
    "targetDir": "clean_photos",
    "removeWatermark": true,
    "watermarkProvider": "aliyun"
  }'
```

## 🔧 配置说明

### 基础配置

编辑 `src/main/resources/application.yml`：

```yaml
material:
  download:
    base-path: ./downloads      # 下载目录
    timeout: 30000             # 超时时间
    max-retry: 3               # 重试次数
```

### AI 去水印配置（可选）

```yaml
watermark-removal:
  enabled: true
  default-provider: aliyun
  
  aliyun:
    enabled: true
    access-key-id: ${ALIYUN_ACCESS_KEY_ID}
    access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
```

**注意：** 建议使用环境变量存储密钥，不要直接写在配置文件中。

## 📁 项目结构

```
giraffe-material-auto/
├── src/main/java/
│   └── com/tcxy/xygj/material/
│       ├── controller/         # API 控制器
│       ├── service/           # 业务服务
│       ├── config/            # 配置类
│       └── dto/               # 数据传输对象
├── src/main/resources/
│   ├── application.yml        # 配置文件
│   └── log4j2.xml            # 日志配置
├── pom.xml                    # Maven 配置
└── README.md                  # 项目说明
```

## 📊 API 文档

详细的 API 文档请查看 [README.md](README.md)

## 🛠️ 技术栈

- **Java 8** - 编程语言
- **Spring Boot 2.6** - 应用框架
- **Jsoup** - HTML 解析
- **OkHttp** - HTTP 客户端
- **Log4j2** - 日志框架
- **Maven** - 项目管理

## ⚠️ 注意事项

1. 请遵守目标网站的 robots.txt 和使用条款
2. 避免频繁请求，建议设置合理的延迟
3. AI 去水印功能需要云服务商的 API 密钥
4. 定期清理下载目录，避免占用过多磁盘空间

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

如有问题或建议，请提交 Issue 或联系开发团队。

---

⭐ 如果这个项目对您有帮助，请给一个 Star 支持一下！

