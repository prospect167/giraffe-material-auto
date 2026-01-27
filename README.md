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
- ✅ **豆瓣高清图优化 V3.0** - 完美支持豆瓣详情页最高清原图（1280×2276），与微信下载同等清晰度
- ✅ **超高清图智能提取 V2.0** - 自动访问详情页获取超高清原图（2000×3000+）
- ✅ **智能高清图提取 V1.0** - 自动识别并下载高清原图而非缩略图（1080×1620）
- ✅ 支持多种图片提取方式（img标签、懒加载、背景图、链接）
- ✅ 自动翻页爬取所有分页图片
- ✅ 自动转换图片为 JPEG 格式
- ✅ **AI 智能去水印** - 支持阿里云、腾讯云、百度云
- ✅ **批量下载多个页面** - 支持并发/串行模式，可配置并发数
- ✅ 失败重试机制（指数退避策略）
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

### 2. 批量下载多个页面

**接口地址：** `POST /api/v1/download/images/batch`

**请求示例：**

```json
{
  "urls": [
    "https://movie.douban.com/photos/photo/1234567890/",
    "https://movie.douban.com/photos/photo/1234567891/",
    "https://movie.douban.com/photos/photo/1234567892/"
  ],
  "targetDir": "batch_downloads",
  "convertToJpeg": true,
  "crawlAllPages": false,
  "concurrent": true,
  "maxConcurrency": 3
}
```

**请求参数说明：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| urls | List<String> | 是 | 页面URL列表（至少1个） |
| targetDir | String | 否 | 目标目录名 |
| concurrent | Boolean | 否 | 是否并发下载，默认true |
| maxConcurrency | Integer | 否 | 最大并发数，默认3 |
| convertToJpeg | Boolean | 否 | 是否转换为JPEG，默认true |
| crawlAllPages | Boolean | 否 | 是否爬取所有分页，默认false |
| removeWatermark | Boolean | 否 | 是否去除水印，默认false |
| watermarkProvider | String | 否 | 去水印服务商: aliyun/tencent/baidu |

**响应示例：**

```json
{
  "code": 200,
  "message": "批量下载完成",
  "data": {
    "success": true,
    "totalPages": 3,
    "successPages": 3,
    "failPages": 0,
    "totalImages": 150,
    "successImages": 145,
    "failImages": 5,
    "totalDuration": 125000,
    "pageResults": [
      {
        "url": "https://movie.douban.com/photos/photo/1234567890/",
        "success": true,
        "totalCount": 50,
        "successCount": 48,
        "failCount": 2,
        "savePath": "./downloads/batch_downloads/20251230_180000",
        "duration": 45000
      }
    ]
  }
}
```

**详细说明：** 查看 [批量下载功能说明.md](./批量下载功能说明.md)

### 3. 健康检查

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

### 批量下载多个页面

```bash
curl -X POST http://localhost:8080/api/v1/download/images/batch \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://movie.douban.com/photos/photo/1234567890/",
      "https://movie.douban.com/photos/photo/1234567891/"
    ],
    "targetDir": "batch_test",
    "concurrent": true,
    "maxConcurrency": 3
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

### 图片提取策略（高清优先）

服务采用多层级提取策略，**优先获取高清原图**：

1. **链接标签高清图**：`<a href="...">` 中的高清图片链接（最高优先级）
2. **高清数据属性**：`data-rawurl`、`data-highres`、`data-original-url` 等
3. **标准 img 标签**：`<img src="...">` 并智能升级为高清版本
4. **懒加载图片**：`<img data-src="...">` 或 `<img data-original="...">`
5. **背景图片**：`style="background-image: url(...)"`
6. **URL 智能升级**：自动将缩略图 URL 转换为高清版本
   - 豆瓣：`/s_ratio_poster/` → `/raw/` 
   - 通用：`_small.jpg` → `_large.jpg`
   - 移除 URL 参数中的尺寸限制

### 高清图优化（V2.0 超高清）

- ✨ **超高清提取 V2.0**：自动访问详情页获取超高清原图（2000×3000+，1-5MB）
  - 智能识别豆瓣相册页面
  - 自动提取并访问图片详情页
  - 三重策略确保获取最高清晰度
  - URL 升级到 `/photo/photo/` 路径（最高清）
  - 📖 查看 [超高清图片优化说明-V2.md](./超高清图片优化说明-V2.md)
  
- ✨ **高清提取 V1.0**：通用高清图提取（1080×1620，200-500KB）
  - 从 HTML 中智能提取高清图片链接
  - URL 升级到 `/photo/raw/` 路径
  - 豆瓣专用优化规则
  - 通用尺寸标识替换
  - 📖 查看 [高清图片下载优化说明.md](./高清图片下载优化说明.md)

**清晰度对比**：
| 版本 | 分辨率 | 文件大小 | 适用场景 |
|------|--------|----------|----------|
| 优化前 | 500×750 | 50-150 KB | 网页浏览 |
| V1.0 | 1080×1620 | 200-500 KB | 一般用途 |
| V2.0 | 2000×3000+ | 1-5 MB | 专业/打印 ⭐ |

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
3. 检查日志中的详细错误信息（已优化，包含完整异常堆栈）
4. 可以增加 timeout 和 max-retry 配置
5. 查看 [下载失败率优化说明.md](./下载失败率优化说明.md) 了解优化方案

### Q: 如何修改下载目录？

A: 修改 `application.yml` 中的 `material.download.base-path` 配置项，或使用 API 参数 `targetDir` 或 `savePath`。详细说明请查看 [保存目录配置说明.md](./保存目录配置说明.md)

### Q: 支持哪些图片格式？

A: 支持所有常见图片格式（JPEG、PNG、GIF、WebP 等），并可以自动转换为 JPEG 格式

### Q: 可以批量下载多个页面吗？

A: ✅ **已支持！** 使用 `/api/v1/download/images/batch` 接口，可以一次性下载多个页面。支持并发和串行两种模式，可配置并发数。详细说明请查看 [批量下载功能说明.md](./批量下载功能说明.md)

### Q: 批量下载时如何选择并发或串行模式？

A: 
- **并发模式**（`concurrent: true`）：速度快，适合网络稳定时使用，建议并发数 3-5
- **串行模式**（`concurrent: false`）：更稳定，适合网络不稳定或需要避免限流时使用

## 📚 相关文档

### 核心文档
- [快速开始.md](./快速开始.md) - 快速上手指南
- [API测试示例.md](./API测试示例.md) - API使用示例和测试方法

### 高清图优化（⭐ 重点推荐）
- [豆瓣高清图优化说明-V3.md](./豆瓣高清图优化说明-V3.md) - 🔥🔥 **V3.0 豆瓣详情页最高清（1280×2276）- 最新！**
- [快速验证-V3优化.md](./快速验证-V3优化.md) - V3.0 快速验证指南
- [超高清图片优化说明-V2.md](./超高清图片优化说明-V2.md) - 🔥 V2.0 超高清提取（2000×3000+）
- [高清图片下载优化说明.md](./高清图片下载优化说明.md) - V1.0 高清提取（1080×1620）
- [快速验证高清图优化.md](./快速验证高清图优化.md) - V1.0/V2.0 验证指南
- [优化总结.md](./优化总结.md) - 完整总结

### 其他功能
- [批量下载功能说明.md](./批量下载功能说明.md) - 批量下载详细说明
- [下载失败率优化说明.md](./下载失败率优化说明.md) - 下载优化方案
- [保存目录配置说明.md](./保存目录配置说明.md) - 目录配置说明
- [水印去除配置说明.md](./水印去除配置说明.md) - AI去水印配置指南
- [日志格式说明.md](./日志格式说明.md) - 日志配置说明
- [部署说明.md](./部署说明.md) - 生产环境部署指南

## 许可证

MIT License

## 联系方式

如有问题或建议，请联系开发团队。

