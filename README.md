# Giraffe Material Auto - 素材自动化下载服务

## 项目简介

这是一个基于 Spring Boot 2.6 的 Web 服务，用于自动从指定的 HTML 页面下载所有图片。

## 技术栈

- Java 8
- Spring Boot 2.6.13
- Jsoup (HTML 解析)
- Apache HttpClient
- Maven

## 功能特性

- ✅ 从任意 HTML 页面提取图片 URL
- ✅ 支持多种图片提取方式（img标签、懒加载、背景图）
- ✅ 自动翻页爬取所有分页图片
- ✅ 自动转换图片为 JPEG 格式
- ✅ **AI 智能去水印** - 支持阿里云、腾讯云、百度云
- ✅ 失败重试机制
- ✅ 并发下载支持
- ✅ RESTful API 接口
- ✅ 详细的下载统计信息

## 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.x+

### 编译项目

```bash
mvn clean package
```

### 启动服务

```bash
java -jar target/giraffe-material-auto-1.0.0-SNAPSHOT.jar
```

或者使用 Maven 启动：

```bash
mvn spring-boot:run
```

服务默认启动在 `http://localhost:8080`

## API 接口文档

### 1. 下载图片

**接口地址：** `POST /api/v1/download/images`

**请求示例：**

```json
{
  "url": "https://movie.douban.com/subject/36686673/all_photos",
  "targetDir": "douban_movie",
  "convertToJpeg": true
}
```

**请求参数说明：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| url | String | 是 | 要爬取的HTML页面URL |
| targetDir | String | 否 | 目标目录名（相对于配置的basePath） |
| convertToJpeg | Boolean | 否 | 是否转换为JPEG格式，默认true |
| crawlAllPages | Boolean | 否 | 是否爬取所有分页，默认false |
| maxPages | Integer | 否 | 最大爬取页数，默认50 |
| removeWatermark | Boolean | 否 | 是否去除水印，默认false |
| watermarkProvider | String | 否 | 去水印服务商: aliyun/tencent/baidu |
| saveOriginal | Boolean | 否 | 是否保存原图，默认false |

**响应示例：**

```json
{
  "code": 200,
  "message": "下载完成",
  "data": {
    "success": true,
    "message": "下载完成",
    "totalCount": 50,
    "successCount": 48,
    "failCount": 2,
    "savePath": "./downloads/douban_movie/20231230_143025",
    "failedUrls": [
      "https://example.com/image1.jpg",
      "https://example.com/image2.jpg"
    ],
    "duration": 15234
  }
}
```

### 2. 健康检查

**接口地址：** `GET /api/v1/download/health`

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": "服务运行正常"
}
```

## 使用示例

### 使用 cURL

```bash
curl -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://movie.douban.com/subject/36686673/all_photos",
    "targetDir": "douban_movie",
    "convertToJpeg": true
  }'
```

### 使用 Postman

1. 创建一个 POST 请求
2. URL: `http://localhost:8080/api/v1/download/images`
3. Headers: `Content-Type: application/json`
4. Body (raw JSON):
```json
{
  "url": "https://movie.douban.com/subject/36686673/all_photos",
  "targetDir": "douban_movie",
  "convertToJpeg": true
}
```

## 配置说明

配置文件位于 `src/main/resources/application.yml`

```yaml
material:
  download:
    # 默认下载目录
    base-path: ./downloads
    # 超时时间（毫秒）
    timeout: 30000
    # 最大重试次数
    max-retry: 3
    # User-Agent
    user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36
```

### 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| base-path | 图片保存的基础路径 | ./downloads |
| timeout | HTTP请求超时时间（毫秒） | 30000 |
| max-retry | 失败重试最大次数 | 3 |
| user-agent | HTTP请求User-Agent | Chrome浏览器标识 |

## 项目结构

```
giraffe-material-auto/
├── src/
│   ├── main/
│   │   ├── java/com/tcxy/xygj/material/
│   │   │   ├── MaterialAutoApplication.java       # 主启动类
│   │   │   ├── config/
│   │   │   │   └── DownloadConfig.java           # 下载配置
│   │   │   ├── controller/
│   │   │   │   └── ImageDownloadController.java  # API控制器
│   │   │   ├── service/
│   │   │   │   └── ImageDownloadService.java     # 核心服务
│   │   │   ├── dto/
│   │   │   │   ├── DownloadRequest.java          # 请求DTO
│   │   │   │   ├── DownloadResponse.java         # 响应DTO
│   │   │   │   └── ApiResponse.java              # 统一响应
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java   # 全局异常处理
│   │   └── resources/
│   │       └── application.yml                    # 配置文件
├── pom.xml                                        # Maven配置
├── .gitignore
└── README.md
```

## 核心功能说明

### 图片提取策略

服务支持多种图片提取方式：

1. **标准 img 标签**：`<img src="...">`
2. **懒加载图片**：`<img data-src="...">` 或 `<img data-original="...">`
3. **背景图片**：`style="background-image: url(...)"`
4. **自动转换相对路径为绝对路径**

### 下载机制

- 自动创建时间戳命名的目录
- 支持自动重试（可配置次数）
- 智能文件名提取和清理
- 可选的 JPEG 格式转换
- 详细的下载日志和统计

## 常见问题

### Q: 下载失败怎么办？

A: 检查以下几点：
1. 目标网站是否可访问
2. 是否需要特殊的 User-Agent 或认证
3. 检查日志中的详细错误信息
4. 可以增加 timeout 和 max-retry 配置

### Q: 如何修改下载目录？

A: 修改 `application.yml` 中的 `material.download.base-path` 配置项

### Q: 支持哪些图片格式？

A: 支持所有常见图片格式（JPEG、PNG、GIF、WebP 等），并可以自动转换为 JPEG 格式

### Q: 可以批量下载多个页面吗？

A: 当前版本需要多次调用 API，每次传入一个 URL。未来版本会支持批量下载。

## 许可证

MIT License

## 联系方式

如有问题或建议，请联系开发团队。

