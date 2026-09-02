# TerraBox Git 推送指南

## 当前状态

**本地分支**: master (领先远程 1 commit)

**未推送的 commits**:
```
5f8c2d3 chore: 清理工作区，移除临时文件和构建脚本
```

**已完成推送的 commits**:
```
323d286 chore: 更新.gitignore, 排除编译输出和数据库文件
db55840 fix: 修复ArenaManager超时保护和ScoreboardManager内存泄漏
6197d12 fix: 修复数据库路径错误和Folia线程违规
ae5cfe7 fix: add missing database configuration to config.yml
b45384d fix: sync missing plugin.yml to root directory
9eee689 fix: database SQL injection and performance fixes
```

## 如何推送

网络恢复后，执行以下命令：

```bash
cd /var/minis/workspace/terrabox
git push origin master
```

## 敏感信息已清除

以下敏感内容已从版本控制中移除：

1. **GIT_AUTH_CONFIG.md** - 包含 GitHub Token（已删除）
2. **远程仓库 URL** - 已从 URL 中移除认证信息（建议轮换 Token）

## 建议操作

### 1. 轮换 GitHub Token（立即执行）

由于 Token 曾暴露在历史中，建议立即：

1. 登录 GitHub → Settings → Developer settings → Personal access tokens
2. 撤销旧 Token: `REDACTED`
3. 生成新 Token（选择 repo 权限）
4. 更新本地配置：
   ```bash
   git remote set-url origin https://用户名:新Token@github.com/741012qwe/TerraBox.git
   ```

### 2. 清理 Git 历史（可选）

如果需要彻底清除历史中的 Token：

```bash
cd /var/minis/workspace/terrabox

# 方法1: 使用 git-filter-repo（推荐）
git filter-repo --replace-text <(echo "REDACTED->")

# 方法2: 使用 BFG Repo-Cleaner
bfg --replace-text REDACTED

# 强制推送（会重写所有协作者的本地历史）
git push origin master --force
```

### 3. 切换到 SSH 认证（推荐）

避免在 URL 中硬编码 Token：

```bash
# 生成 SSH Key（如果没有）
ssh-keygen -t ed25519 -C "your_email@example.com"

# 添加到 GitHub
cat ~/.ssh/id_ed25519.pub
# 复制输出到 GitHub → Settings → SSH and GPG keys

# 切换远程地址
git remote set-url origin git@github.com:741012qwe/TerraBox.git
```

## 文件清理统计

**已删除**：
- out/ (编译缓存目录)
- srcs.txt (构建中间文件)
- test.sh, test_config.yml (测试脚本)
- build.sh, fix_issues.sh, optimize_loot.sh, rollback.sh (构建脚本)
- GIT_AUTH_CONFIG.md (敏感信息文档)

**保留**：
- src/ (48个Java源文件)
- config.yml, plugin.yml
- TerraBox-1.0.0.jar
- *.md (文档)

## 部署目录同步状态

- ✅ 工作区: /var/minis/workspace/terrabox/
- ✅ 部署目录: /var/minis/mounts/AI工作目录/物资大陆TerraBox/
- ✅ 两者已同步（JAR + 源码 + 配置）
