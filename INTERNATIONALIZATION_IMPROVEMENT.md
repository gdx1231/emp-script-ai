# AiStreamOrPost.java 国际化改进说明

## 概述
为 `AiStreamOrPost.java` 类实现了完整的国际化支持，将所有硬编码的中文字符串提取到消息映射中，并根据系统语言设置返回相应的中英文消息。

## 实现方案

### 1. 消息映射结构
创建了静态的二级映射结构：
```java
private static final Map<String, Map<String, String>> MESSAGES = new HashMap<>();
```

### 2. 支持的语言
- **zhcn**: 中文（默认）
- **enus**: 英文

### 3. 消息键值对照表

| 消息键                  | 中文                                             | 英文                                             |
| ----------------------- | ------------------------------------------------ | ------------------------------------------------ |
| NO_PROMPTS_NO_ACTION    | 没有定义提示词，且没有步骤动作 action，stepName= | No prompts defined and no step action, stepName= |
| ACTION_EXECUTION_ERROR  | 执行动作发生错误，                               | Error executing action,                          |
| GENERAL_ERROR           | 发生错误，                                       | Error occurred,                                  |
| UNSUPPORTED_AI_PROVIDER | 不支持的AI提供商 ai_provider，                   | Unsupported AI provider ai_provider,             |
| REQUEST_SUCCESS         | 请求成功                                         | Request successful                               |
| SYSTEM_ERROR            | 系统发生错误：                                   | System error occurred:                           |
| STEP_ACTION_ERROR       | 执行步骤动作时发生错误：                         | Error executing step action:                     |
| GLOBAL_EXCEPTION_ERROR  | 处理全局异常时发生错误：                         | Error handling global exception:                 |
| AI_REQUEST_ERROR        | AI请求处理错误：                                 | AI request processing error:                     |
| EXPORT_RESULT           | 导出结果：                                       | Export result:                                   |

## 核心方法

### getMessage(String key)
```java
private String getMessage(String key) {
    String lang = "zhcn"; // 默认中文
    if (chatManager != null && chatManager.getRv() != null) {
        String requestLang = chatManager.getRv().getLang();
        if ("enus".equalsIgnoreCase(requestLang)) {
            lang = "enus";
        }
    }
    
    Map<String, String> langMessages = MESSAGES.get(lang);
    if (langMessages != null && langMessages.containsKey(key)) {
        return langMessages.get(key);
    }
    
    // 如果找不到对应语言的消息，返回中文默认消息
    Map<String, String> defaultMessages = MESSAGES.get("zhcn");
    return defaultMessages.getOrDefault(key, key);
}
```

## 语言检测机制

### 语言判断逻辑
1. 从 `chatManager.getRv().getLang()` 获取请求语言
2. 如果语言是 "enus"（忽略大小写），使用英文消息
3. 否则使用中文消息（默认）
4. 如果找不到对应语言的消息，回退到中文默认消息

### 与现有系统的兼容性
- 兼容现有的 `RequestValue.getLang()` 方法
- 遵循现有的语言代码规范（zhcn/enus）
- 与 `ActionEventsOut` 类的语言处理保持一致

## 修改的代码位置

### 1. 错误消息
```java
// 原来
JSONObject rst = UJSon.rstFalse("没有定义提示词，且没有步骤动作 action，stepName=" + chatManager.getStepName());

// 修改后
JSONObject rst = UJSon.rstFalse(getMessage("NO_PROMPTS_NO_ACTION") + chatManager.getStepName());
```

### 2. 日志消息
```java
// 原来
LOGGER.info("导出结果：{}", grpInfo.toString(2));

// 修改后
LOGGER.info(getMessage("EXPORT_RESULT") + "{}", grpInfo.toString(2));
```

### 3. 异常处理消息
```java
// 原来
LOGGER.error("处理全局异常时发生错误：{}", e1.getMessage(), e1);

// 修改后
LOGGER.error(getMessage("GLOBAL_EXCEPTION_ERROR") + "{}", e1.getMessage(), e1);
```

## 优势

### 1. 用户体验提升
- 根据用户语言偏好显示相应的错误消息
- 提供一致的多语言体验

### 2. 代码维护性
- 所有消息集中管理
- 新增语言支持只需添加消息映射
- 易于维护和更新

### 3. 扩展性
- 可以轻松添加更多语言支持
- 支持更多消息类型
- 可以添加更复杂的消息格式化

### 4. 一致性
- 与现有系统的语言机制保持一致
- 遵循项目的国际化规范

## 使用示例

### 中文环境
```
请求参数: rv.getLang() = "zhcn"
输出: "没有定义提示词，且没有步骤动作 action，stepName=testStep"
```

### 英文环境
```
请求参数: rv.getLang() = "enus" 
输出: "No prompts defined and no step action, stepName=testStep"
```

## 编译状态
✅ 编译成功，无错误

## 后续改进建议
1. 考虑使用资源文件（.properties）来管理消息
2. 添加更多语言支持（如日语、韩语等）
3. 实现消息参数化，支持更复杂的格式化
4. 添加消息版本管理机制
5. 考虑添加消息缓存机制以提高性能
