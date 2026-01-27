# 🔐 豆瓣Cookie配置说明

## ⚠️ 问题原因

豆瓣对爬虫有**严格的JavaScript拦截机制**，Jsoup无法执行JavaScript，因此无法绕过"点我继续浏览"的拦截页面。

## ✅ 解决方案：配置有效的Cookie

### 步骤1：获取豆瓣Cookie

1. **打开浏览器**（推荐Chrome/Edge）

2. **访问豆瓣图片页面**
   ```
   https://movie.douban.com/photos/photo/2925525013/
   ```

3. **点击"点我继续浏览"按钮**

4. **打开开发者工具**
   - Mac: `Command + Option + I`
   - Windows: `F12` 或 `Ctrl + Shift + I`

5. **切换到Network（网络）标签**

6. **刷新页面** (`F5` 或 `Command+R`)

7. **找到第一个请求**
   - 在Network面板中，找到名为 `2925525013/` 的请求
   - 点击该请求

8. **复制Cookie**
   - 在右侧面板中，找到 `Request Headers`（请求头）
   - 找到 `Cookie:` 行
   - 复制整个Cookie值（很长的一串文本）

   示例格式：
   ```
   bid=xxx; dbcl2=xxx; ck=xxx; _pk_id=xxx; ...
   ```

### 步骤2：配置到application.yml

1. **打开配置文件**
   ```bash
   src/main/resources/application.yml
   ```

2. **找到 `douban-cookie` 配置项**（约30行）
   ```yaml
   material:
     download:
       # ... 其他配置 ...
       douban-cookie: ""  # ← 在这里粘贴Cookie
   ```

3. **粘贴Cookie**
   ```yaml
   douban-cookie: "bid=xxx; dbcl2=xxx; ck=xxx; _pk_id=xxx; ..."
   ```

4. **保存文件**

### 步骤3：重启服务

```bash
cd /Users/prospect/Documents/code/tcxy/xygj/giraffe-material-auto
mvn spring-boot:run
```

## 🧪 测试

重启后，使用相同的参数测试：

```json
{
    "url": "https://movie.douban.com/photos/photo/2925525013/",
    "targetDir": "douban_movie",
    "convertToJpeg": true,
    "crawlAllPages": true
}
```

**期望结果**：应该能成功提取并下载所有252张高清图片！

## 📝 注意事项

1. **Cookie有效期**：豆瓣Cookie通常几天到几周后会过期，届时需要重新获取
2. **隐私保护**：Cookie包含登录信息，请勿分享给他人
3. **格式检查**：确保Cookie是一整行文本，没有换行符
4. **引号包裹**：在YAML中，Cookie值用双引号包裹

## 🆘 故障排查

### 如果仍然提示"未找到任何图片"

1. **检查Cookie是否正确配置**
   - 查看日志是否有 "使用豆瓣Cookie进行请求" 的DEBUG信息

2. **检查Cookie是否过期**
   - 在浏览器中重新访问豆瓣页面
   - 如果需要重新点击"继续浏览"，说明Cookie已过期
   - 重新获取Cookie并配置

3. **检查网络连接**
   - 确保服务器能访问豆瓣网站

4. **查看详细日志**
   ```bash
   tail -f logs/giraffe-material-auto.log
   ```

## 🎉 完成

配置完成后，系统应该能正常下载豆瓣高清图片了！

