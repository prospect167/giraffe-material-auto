# API 测试示例

## 1. 豆瓣电影图片下载

### cURL 命令

```bash
curl -X POST http://localhost:8080/api/v1/download/images \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://movie.douban.com/subject/36686673/all_photos",
    "targetDir": "douban_movie",
    "convertToJpeg": true
  }'
```

### 预期响应

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
    "failedUrls": [],
    "duration": 15234
  }
}
```

## 2. 批量下载多个页面

### cURL 命令（并发模式）

```bash
curl -X POST http://localhost:8080/api/v1/download/images/batch \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://movie.douban.com/photos/photo/1234567890/",
      "https://movie.douban.com/photos/photo/1234567891/",
      "https://movie.douban.com/photos/photo/1234567892/"
    ],
    "targetDir": "batch_downloads",
    "convertToJpeg": true,
    "concurrent": true,
    "maxConcurrency": 3
  }'
```

### cURL 命令（串行模式）

```bash
curl -X POST http://localhost:8080/api/v1/download/images/batch \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://movie.douban.com/photos/photo/1234567890/",
      "https://movie.douban.com/photos/photo/1234567891/"
    ],
    "targetDir": "batch_downloads",
    "concurrent": false
  }'
```

### 预期响应

```json
{
  "code": 200,
  "message": "批量下载完成",
  "data": {
    "success": true,
    "message": "所有页面下载完成",
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
        "message": "下载完成",
        "totalCount": 50,
        "successCount": 48,
        "failCount": 2,
        "savePath": "./downloads/batch_downloads/20251230_180000",
        "failedUrls": ["https://example.com/image1.jpg"],
        "duration": 45000
      }
    ]
  }
}
```

## 3. 测试健康检查

### cURL 命令

```bash
curl http://localhost:8080/api/v1/download/health
```

### 预期响应

```json
{
  "code": 200,
  "message": "success",
  "data": "服务运行正常"
}
```

## 4. 使用 HTTPie 测试

如果安装了 HTTPie：

```bash
# 单页面下载
http POST http://localhost:8080/api/v1/download/images \
  url="https://movie.douban.com/subject/36686673/all_photos" \
  targetDir="douban_movie" \
  convertToJpeg:=true

# 批量下载
http POST http://localhost:8080/api/v1/download/images/batch \
  urls:='["https://example.com/page1", "https://example.com/page2"]' \
  targetDir="batch_test" \
  concurrent:=true \
  maxConcurrency:=3
```

## 5. 使用 Python 测试

```python
import requests
import json

url = "http://localhost:8080/api/v1/download/images"
payload = {
    "url": "https://movie.douban.com/subject/36686673/all_photos",
    "targetDir": "douban_movie",
    "convertToJpeg": True
}
headers = {
    "Content-Type": "application/json"
}

response = requests.post(url, data=json.dumps(payload), headers=headers)
print(response.json())
```

## 5. 使用 Python 测试（批量下载）

```python
import requests
import json

# 批量下载
url = "http://localhost:8080/api/v1/download/images/batch"
payload = {
    "urls": [
        "https://movie.douban.com/photos/photo/1234567890/",
        "https://movie.douban.com/photos/photo/1234567891/"
    ],
    "targetDir": "batch_test",
    "concurrent": True,
    "maxConcurrency": 3
}
headers = {
    "Content-Type": "application/json"
}

response = requests.post(url, data=json.dumps(payload), headers=headers)
result = response.json()

print(f"总页面数: {result['data']['totalPages']}")
print(f"成功页面数: {result['data']['successPages']}")
print(f"总图片数: {result['data']['totalImages']}")
print(f"成功图片数: {result['data']['successImages']}")

# 打印每个页面的结果
for page_result in result['data']['pageResults']:
    print(f"\n页面: {page_result['url']}")
    print(f"  成功: {page_result['successCount']}/{page_result['totalCount']}")
```

## 6. 使用 JavaScript (Node.js) 测试

```javascript
const axios = require('axios');

// 单页面下载
const singleData = {
  url: 'https://movie.douban.com/subject/36686673/all_photos',
  targetDir: 'douban_movie',
  convertToJpeg: true
};

axios.post('http://localhost:8080/api/v1/download/images', singleData)
  .then(response => {
    console.log(JSON.stringify(response.data, null, 2));
  })
  .catch(error => {
    console.error('Error:', error.message);
  });

// 批量下载
const batchData = {
  urls: [
    'https://movie.douban.com/photos/photo/1234567890/',
    'https://movie.douban.com/photos/photo/1234567891/'
  ],
  targetDir: 'batch_test',
  concurrent: true,
  maxConcurrency: 3
};

axios.post('http://localhost:8080/api/v1/download/images/batch', batchData)
  .then(response => {
    const result = response.data.data;
    console.log(`总页面数: ${result.totalPages}`);
    console.log(`成功页面数: ${result.successPages}`);
    console.log(`总图片数: ${result.totalImages}`);
    console.log(`成功图片数: ${result.successImages}`);
    
    result.pageResults.forEach(page => {
      console.log(`\n页面: ${page.url}`);
      console.log(`  成功: ${page.successCount}/${page.totalCount}`);
    });
  })
  .catch(error => {
    console.error('Error:', error.message);
  });
```

## 7. Postman 导入配置

```json
{
  "info": {
    "name": "Giraffe Material Auto API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "下载图片",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"url\": \"https://movie.douban.com/subject/36686673/all_photos\",\n  \"targetDir\": \"douban_movie\",\n  \"convertToJpeg\": true\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/v1/download/images",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "v1", "download", "images"]
        }
      }
    },
    {
      "name": "批量下载图片",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"urls\": [\n    \"https://movie.douban.com/photos/photo/1234567890/\",\n    \"https://movie.douban.com/photos/photo/1234567891/\"\n  ],\n  \"targetDir\": \"batch_downloads\",\n  \"concurrent\": true,\n  \"maxConcurrency\": 3\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/v1/download/images/batch",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "v1", "download", "images", "batch"]
        }
      }
    },
    {
      "name": "健康检查",
      "request": {
        "method": "GET",
        "url": {
          "raw": "http://localhost:8080/api/v1/download/health",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "v1", "download", "health"]
        }
      }
    }
  ]
}
```

## 注意事项

1. 确保服务已启动且运行在 8080 端口
2. 某些网站可能需要特殊的 User-Agent 或有反爬虫机制
3. 下载大量图片时可能需要较长时间
4. 建议首次测试使用图片较少的页面
5. 下载的图片会保存在配置的 `base-path` 目录下
6. **批量下载建议：**
   - 网络稳定时使用并发模式（`concurrent: true`），并发数建议 3-5
   - 网络不稳定时使用串行模式（`concurrent: false`）
   - 单次批量下载的页面数建议 ≤ 50
7. 详细说明请查看 [批量下载功能说明.md](./批量下载功能说明.md)

