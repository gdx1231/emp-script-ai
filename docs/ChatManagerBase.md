# ChatManagerBase 类说明文档

## 概述

`ChatManagerBase` 是 EMP Script AI 项目的核心类，负责管理AI聊天会话的完整生命周期。该类提供了一个统一的接口来处理AI聊天系统的各个方面，从会话管理到消息处理，从API调用到数据持久化。

## 文件位置

```
src/main/java/com/gdxsoft/ai/ChatManagerBase.java
```

## 主要功能模块

### 1. 聊天会话管理
- **功能**: 创建、维护和管理AI聊天会话
- **关键方法**: 
  - `getOrNewAiChat()` - 获取或创建新的聊天会话
  - `checkParams()` - 验证会话参数
- **特性**: 支持会话持久化和状态管理

### 2. 消息处理
- **功能**: 处理用户输入、AI响应和系统消息
- **关键方法**:
  - `addAiChatMsg()` - 添加聊天消息
  - `updateAiChatMsg()` - 更新聊天消息
  - `appendPreviousMessages()` - 加载历史消息
- **特性**: 支持消息分类、时间戳和元数据管理

### 3. AI模式管理
- **功能**: 加载和管理不同的AI工作模式
- **关键方法**:
  - `loadModes()` - 静态方法加载AI模式配置
  - `checkProviderAndModel()` - 验证AI提供商和模型
- **特性**: 支持多种AI提供商和模型配置

### 4. 提示词管理
- **功能**: 处理和组织AI提示词
- **关键方法**:
  - `appendPrompts()` - 添加提示词到请求数据
  - `processPrompt()` - 处理单个提示词
- **特性**: 支持多阶段提示词处理和条件过滤

### 5. API调用管理
- **功能**: 管理外部API的调用和集成
- **关键方法**:
  - `apiToolsChecks()` - 执行API工具检查
  - `processApiCheckPrompt()` - 处理API检查提示词
  - `executeApiCall()` - 执行单个API调用
- **特性**: 支持智能API选择和参数传递

### 6. 动作执行
- **功能**: 执行自定义的AI动作和操作
- **关键方法**:
  - `doAction()` - 执行AI动作
  - `loadAction()` - 加载动作类
- **特性**: 支持动态加载和缓存机制

### 7. 数据持久化
- **功能**: 管理聊天记录和消息的数据库存储
- **关键方法**:
  - `updateAiChatMsgTokens()` - 更新Token使用统计
- **特性**: 支持事务性操作和数据完整性

### 8. 事件处理
- **功能**: 处理实时输出事件和用户交互
- **关键方法**:
  - `outEvent()` - 输出事件到客户端
  - `setOutEvents()` - 设置事件处理器
- **特性**: 支持流式输出和实时反馈

## 核心特性

### 多AI提供商支持
- OpenAI
- Anthropic
- 其他兼容的AI服务提供商

### 响应模式
- **流式响应**: 实时输出AI生成内容
- **非流式响应**: 一次性返回完整结果
- **思考模式**: 启用AI的推理过程展示

### 安全性
- 参数化SQL查询防止注入攻击
- 请求参数验证和清理
- 线程安全的会话管理

### 国际化
- 完整的中英文支持
- 动态语言切换
- 错误消息本地化

## 使用示例

### 基本使用流程

```java
// 1. 创建ChatManager实例
ChatManagerBase manager = new ChatManagerBase(requestValue, dbConfig, writer);

// 2. 设置输出事件处理器（可选）
manager.setOutEvents(new CustomOutEvents());

// 3. 检查和验证参数
JSONObject result = manager.checkParams();
if (!result.optBoolean("RST")) {
    // 处理参数错误
    System.out.println("参数错误: " + result.optString("MSG"));
    return;
}

// 4. 创建AI请求数据
IRequestData requestData = manager.createRequestData();

// 5. 添加提示词
manager.appendPrompts(requestData);

// 6. 执行AI请求
IRequestAI aiRequest = manager.createRequestAI();
String response = aiRequest.doPost(requestData);

// 7. 处理响应
JSONObject responseJson = aiRequest.extraceJson(response, true);
```

### 执行自定义动作

```java
ChatManagerBase manager = new ChatManagerBase(requestValue, dbConfig, writer);

// 检查参数并加载动作
JSONObject result = manager.checkParams();
if (result.optBoolean("RST") && manager.getStepAction() != null) {
    // 执行动作
    manager.doAction();
}
```

### API工具调用

```java
// 配置支持API检查的提示词
// 在XML配置中设置 apisCheck="true"
// 系统会自动检查是否需要调用外部API并执行调用
```

## 配置要求

### 数据库表结构
- `AI_CHAT` - 聊天会话表
- `AI_CHAT_MSG` - 聊天消息表
- `AI_PROVIDER` - AI提供商配置表
- `AI_PROVIDER_MODEL` - AI模型配置表
- `AI_PROVIDER_URL` - API端点配置表

### 环境变量
- 数据库连接配置
- AI服务API密钥
- 日志配置

### XML配置文件
- AI模式定义
- 提示词配置
- API配置
- 动作配置

## 最佳实践

### 1. 错误处理
```java
try {
    manager.appendPrompts(requestData);
} catch (Exception e) {
    LOGGER.error("处理提示词时出错", e);
    // 适当的错误处理
}
```

### 2. 资源管理
```java
// 确保在不需要时清理缓存
ChatManagerBase.removeRequestAI(cacheKey);
```

### 3. 性能优化
```java
// 复用RequestAI实例
String cacheKey = "ai_" + provider + "_" + model;
IRequestAI existingRequest = ChatManagerBase.getRequestAI(cacheKey);
if (existingRequest == null) {
    existingRequest = manager.createRequestAI();
    ChatManagerBase.putRequestAI(cacheKey, existingRequest);
}
```

## 扩展点

### 1. 自定义输出事件处理器
```java
public class CustomOutEvents implements IOutEvents {
    @Override
    public void outEvent(String message, PrintWriter writer) {
        // 自定义事件处理逻辑
    }
}
```

### 2. 自定义动作类
```java
public class CustomAction implements IAction {
    @Override
    public JSONObject doAction(RequestValue rv, String fullText) throws Exception {
        // 自定义动作实现
        return UJSon.rstTrue("操作完成");
    }
    
    @Override
    public String createPrompt(RequestValue rv, String dbConfigName) throws Exception {
        // 自定义提示词生成
        return "自定义提示词内容";
    }
}
```

## 依赖项

### 核心依赖
- **EasyWeb框架**: `com.gdxsoft.easyweb.*`
- **JSON处理**: `org.json.*`
- **Apache Commons**: `org.apache.commons.*`
- **SLF4J日志**: `org.slf4j.*`

### 内部依赖
- `com.gdxsoft.ai.modes.*` - AI模式管理
- `com.gdxsoft.ai.request.*` - AI请求处理
- `com.gdxsoft.ai.export.*` - 动作导出接口

## 注意事项

### 线程安全
- 使用 `ConcurrentHashMap` 管理AI请求实例缓存
- 避免在多线程环境中共享 `RequestValue` 实例

### 性能考虑
- 合理使用实例缓存减少对象创建开销
- 避免频繁的数据库查询
- 使用流式处理处理大量数据

### 安全考虑
- 所有数据库操作使用参数化查询
- 验证所有用户输入
- 敏感信息（如API密钥）不记录到日志

## 版本历史

### v1.0 (2025-09-06)
- 初始版本创建
- 实现基础聊天管理功能
- 支持多AI提供商

### v1.1 (2025-09-07)
- 添加API检查和调用功能
- 重构代码结构提高可维护性
- 完善错误处理和日志记录

## 许可证

Copyright (c) 2025 GDX Software. All rights reserved.

---

*最后更新: 2025年9月7日*
