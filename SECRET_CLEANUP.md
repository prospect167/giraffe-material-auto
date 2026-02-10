# 清理Git历史中的敏感信息

GitHub检测到历史提交中包含敏感信息（VolcEngine Access Key ID），需要清理git历史。

## 方法1：使用GitHub提供的链接（推荐）

访问GitHub提供的链接，允许该密钥（如果确认密钥已失效）：
https://github.com/prospect167/giraffe-material-auto/security/secret-scanning/unblock-secret/38sp94Toxufnf0a04ZPtD4sH87O

## 方法2：从Git历史中移除敏感信息

如果密钥仍然有效，需要从git历史中移除：

### 使用 git filter-branch（适用于小仓库）

```bash
# 备份仓库
git clone --mirror https://github.com/prospect167/giraffe-material-auto.git backup.git

# 从所有提交中移除敏感信息
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch src/main/resources/application.yml" \
  --prune-empty --tag-name-filter cat -- --all

# 强制推送（危险操作，请确保已备份）
git push origin --force --all
git push origin --force --tags
```

### 使用 BFG Repo-Cleaner（推荐，更快）

```bash
# 下载 BFG: https://rtyley.github.io/bfg-repo-cleaner/

# 替换敏感信息
java -jar bfg.jar --replace-text passwords.txt giraffe-material-auto.git

# 清理和推送
cd giraffe-material-auto.git
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git push --force
```

## 方法3：创建新分支（最简单）

如果历史提交不重要，可以：
1. 创建新分支
2. 删除旧分支
3. 将新分支设为默认分支

```bash
git checkout -b main-clean
git push origin main-clean
# 在GitHub上设置 main-clean 为默认分支
# 删除 main 分支
```

## 当前状态

- ✅ 配置文件已更新，不再包含硬编码密钥
- ✅ .gitignore 已更新，忽略 application-local.yml
- ✅ 已创建 application-local.yml.example 作为配置模板

## 配置密钥的方式

### 方式1：环境变量（推荐生产环境）
```bash
export JIMENG_API_KEY=你的API密钥
export JIMENG_SECRET_KEY=你的Secret密钥
```

### 方式2：本地配置文件（推荐开发环境）
```bash
# 复制示例文件
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 编辑并填写实际密钥
vim src/main/resources/application-local.yml
```

