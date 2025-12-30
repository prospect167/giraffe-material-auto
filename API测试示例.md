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

## 2. 测试健康检查

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

## 3. 使用 HTTPie 测试

如果安装了 HTTPie：

```bash
http POST http://localhost:8080/api/v1/download/images \
  url="https://movie.douban.com/subject/36686673/all_photos" \
  targetDir="douban_movie" \
  convertToJpeg:=true
```

## 4. 使用 Python 测试

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

## 5. 使用 JavaScript (Node.js) 测试

```javascript
const axios = require('axios');

const data = {
  url: 'https://movie.douban.com/subject/36686673/all_photos',
  targetDir: 'douban_movie',
  convertToJpeg: true
};

axios.post('http://localhost:8080/api/v1/download/images', data)
  .then(response => {
    console.log(JSON.stringify(response.data, null, 2));
  })
  .catch(error => {
    console.error('Error:', error.message);
  });
```

## 6. Postman 导入配置

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

