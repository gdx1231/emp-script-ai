# ChatManagerBase 国际化字符串列表（静态常量版本）

## 概述
本文档列出了从 `ChatManagerBase.java` 文件中提取的所有国际化字符串，现已重构为使用静态常量管理。这些字符串会根据 `this.en` 参数的值自动切换为中文或英文显示。

## 新架构优势
- **类型安全**: 使用静态常量避免字符串拼写错误
- **集中管理**: 所有国际化字符串集中在 `ChatManagerI18nConstants` 类中
- **易于维护**: 常量按功能分类，便于查找和修改
- **代码提示**: IDE可以提供完整的代码补全支持
- **重构友好**: 重命名常量时IDE可以自动更新所有引用

## 语言切换机制
- 通过 `boolean en` 字段控制语言切换
- 在构造函数中从请求参数 `rv.s("en")` 获取语言设置
- 使用私有方法 `getText(String key, String chineseText, String englishText)` 进行文本国际化

## 国际化字符串列表

### 1. 日志信息类

| 键名                   | 中文                           | 英文                                     | 使用位置            |
| ---------------------- | ------------------------------ | ---------------------------------------- | ------------------- |
| `model.request.params` | 模型请求参数：{}               | Model request parameters: {}             | createRequestData() |
| `message.not.json`     | 读取的消息不是JSON格式，忽略： | Message is not in JSON format, ignoring: | doAction()          |
| `export.result`        | 导出结果：{}                   | Export result: {}                        | doAction()          |
| `ai.chat.record`       | AI聊天记录：{}                 | AI chat record: {}                       | checkParams()       |
| `action.loading`       | 加载 actionName={}, 类名：{}   | Loading actionName={}, class: {}         | loadAction()        |

### 2. 错误信息类

| 键名                         | 中文                           | 英文                                                   | 使用位置                |
| ---------------------------- | ------------------------------ | ------------------------------------------------------ | ----------------------- |
| `error.no.request.id`        | 无请求ID requestId，           | No request ID provided                                 | checkParams()           |
| `error.no.ai.provider`       | 无AI提供商 ai_provider，       | No AI provider specified                               | checkParams()           |
| `error.no.ai.model`          | 无AI模型 ai_model，            | No AI model specified                                  | checkParams()           |
| `error.no.ai.mode`           | 无AI模式mode                   | No AI mode specified                                   | checkParams()           |
| `error.mode.not.found`       | 找不到模式：                   | Mode not found:                                        | checkParams()           |
| `error.step.not.found`       | 找不到步骤Step=：              | Step not found:                                        | checkParams()           |
| `action.load.failed`         | 加载Action失败, {}             | Failed to load Action, {}                              | checkParams()           |
| `error.action.load.failed`   | 加载Action失败：               | Failed to load Action:                                 | checkParams()           |
| `error.api.url.empty`        | AI接口地址api_url不能为空      | AI API URL cannot be empty                             | checkParams()           |
| `error.model.not.exist`      | 模型不存在：{模型},{供应商}    | Model does not exist: {model}, Provider: {provider}    | checkProviderAndModel() |
| `error.model.offline.0`      | 模型已下线0：{模型},{供应商}   | Model is offline (0): {model}, Provider: {provider}    | checkProviderAndModel() |
| `error.model.offline.1`      | 模型已下线1：{模型},{供应商}   | Model is offline (1): {model}, Provider: {provider}    | checkProviderAndModel() |
| `error.api.config.not.exist` | API配置不存在,供应商：{供应商} | API configuration does not exist, Provider: {provider} | checkProviderAndModel() |
| `error.general`              | 错误：{错误信息}               | Error: {error message}                                 | checkProviderAndModel() |

### 3. 状态提示类

| 键名              | 中文          | 英文        | 使用位置                |
| ----------------- | ------------- | ----------- | ----------------------- |
| `action.creating` | 正在创建中... | Creating... | doAction()              |
| `success.ok`      | OK            | OK          | checkProviderAndModel() |

## 新的使用方式

### 1. 在代码中使用静态常量
```java
// 使用分类常量（推荐）
LOGGER.info(getText(LogMessages.MODEL_REQUEST_PARAMS), modelData);
JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_REQUEST_ID));
String status = getText(StatusMessages.ACTION_CREATING);

// 使用格式化参数
String error = getText(ErrorMessages.ERROR_MODEL_NOT_EXIST, modelName, providerName);
```

### 2. 直接使用工具类（用于其他类）
```java
// 在其他类中直接使用
String message = ChatManagerI18nConstants.getText(LogMessages.MODEL_REQUEST_PARAMS, true, "GPT-4");
```

### 3. 常量分类结构
- **LogMessages**: 日志信息类常量
- **ErrorMessages**: 错误信息类常量  
- **StatusMessages**: 状态提示类常量

## 核心文件

### 主要类文件
- `ChatManagerBase.java` - AI聊天管理器主类
- `ChatManagerI18nConstants.java` - 国际化常量管理类
- `ChatManagerInternationalizationExample.java` - 使用示例

### 新增的方法和字段
- `ChatManagerI18nConstants.getText(String key, boolean useEnglish, Object... args)` - 静态工具方法
- `ChatManagerI18nConstants.I18N_MESSAGES` - 国际化字符串映射
- `LogMessages/ErrorMessages/StatusMessages` - 分类常量类

### 语言切换机制（保持不变）
- 通过 `boolean en` 字段控制语言切换
- 在构造函数中从请求参数 `rv.isEn()` 获取语言设置
- 使用私有方法 `getText(String key, Object... args)` 进行文本国际化

## 迁移指南

### 从旧版本迁移
如果您之前使用的是旧版本的 `getText(key, chineseText, englishText)` 方法：

1. **替换方法调用**：
```java
// 旧版本
getText("error.no.request.id", "无请求ID", "No request ID")

// 新版本
getText(ErrorMessages.ERROR_NO_REQUEST_ID)
```

2. **使用常量类**：
```java
// 导入静态常量
import static com.gdxsoft.ai.ChatManagerI18nConstants.*;

// 使用分类常量
getText(ErrorMessages.ERROR_NO_REQUEST_ID)
getText(LogMessages.MODEL_REQUEST_PARAMS)
getText(StatusMessages.ACTION_CREATING)
```

### 添加新的国际化字符串
1. 在 `ChatManagerI18nConstants.I18N_MESSAGES` 中添加映射
2. 在对应的常量类中添加静态常量
3. 在代码中使用新常量

```java
// 1. 添加到映射中
put("NEW_MESSAGE_KEY", new String[] { "中文文本", "English text" });

// 2. 添加常量
public static final String NEW_MESSAGE_KEY = "NEW_MESSAGE_KEY";

// 3. 使用
getText(ErrorMessages.NEW_MESSAGE_KEY)
```

## 示例代码
详细的使用示例请参考 `ChatManagerInternationalizationExample.java` 文件。