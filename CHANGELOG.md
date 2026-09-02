# TerraBox v2.4.16 Changelog

## 消息和日志修复

### 问题描述
- 聊天框显示英文代码和变量名
- 服务器日志输出异常详细信息
- 颜色代码解析不一致（& vs §）

### 修复内容

1. **颜色代码统一**
   - 添加 `convertColorCode()` 方法统一转换 & → §
   - 添加 `prepareForAdventure()` 方法处理Adventure序列化
   - 所有消息统一使用 `plugin.msg()` 或 `plugin.component()`

2. **缺失消息提示**
   - 消息缺失时显示 `[消息缺失: key]` 而非原始key
   - 避免用户看到未配置的键名

3. **日志输出清理**
   - 移除所有 `printStackTrace()` 调用
   - 移除异常详细信息中的 `getMessage()` 输出
   - 统一错误信息显示格式

4. **Title/BossBar消息**
   - 倒计时Title使用 `§` 颜色代码
   - BossBar使用Adventure组件序列化
   - ActionBar统一使用 `convertColorCode()`

### 修改文件
- TerraBoxPlugin.java - 添加颜色代码工具方法
- GameListener.java - 修复旁观模式消息
- GameManager.java - 修复命令消息
- ScoreboardManager.java - 修复日志输出
- BoxManager.java - 移除堆栈追踪
- 其他文件 - 清理硬编码消息

### 后续计划
- [ ] 完成所有TODO消息的替换
- [ ] 添加消息本地化支持
- [ ] 优化颜色代码渲染性能
