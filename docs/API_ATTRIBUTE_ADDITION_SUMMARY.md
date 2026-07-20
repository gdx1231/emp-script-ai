# Step 和 Prompt 类 API 属性添加总结

## 修改概述

根据用户需求，为 `Step.java` 和 `Prompt.java` 类添加了 `api` String 属性，并同步更新了 `ModeParser.java` 中的解析逻辑以及相关的克隆和测试功能。

## 主要变更

### 1. Step.java 修改

**新增属性**：
```java
// optional API reference name for API calls
private String api;
```

**新增方法**：
- `public String getApi()` - 获取API引用名称的getter方法
- `public void setApi(String api)` - 设置API引用名称的setter方法

**特点**：
- 遵循项目规范，添加了详细的中文注释
- 保持与现有代码风格一致
- 提供完整的getter/setter方法

### 2. Prompt.java 修改

**新增属性**：
```java
// API reference name for API calls
private String api;
```

**新增方法**：
- `public String getApi()` - 获取API引用名称的getter方法，包含详细中文注释
- `public void setApi(String api)` - 设置API引用名称的setter方法，包含详细中文注释

**特点**：
- 遵循参数类创建规范，包含详细的中文注释
- 属性命名简洁明确
- 与现有属性风格保持一致

### 3. ModeParser.java 修改

**Step解析增强**：
```java
String stepApi = stepElement.getAttribute("api");
// ... 
if (stepApi != null && stepApi.trim().length() > 0) {
    step.setApi(stepApi.trim());
}
```

**Prompt解析增强**：
```java
String api = promptElement.getAttribute("api");
// ...
if (api != null && api.trim().length() > 0) {
    p.setApi(api.trim());
}
```

**特点**：
- 遵循现有解析模式
- 包含空值检查和trim处理
- 保持代码一致性

### 4. Mode.java 修改

**克隆方法增强**：

**Prompt克隆**：
```java
if (p.getApi() != null) {
    np.setApi(p.getApi());
}
```

**Step克隆**：
```java
if (s.getApi() != null) {
    ns.setApi(s.getApi());
}
```

**特点**：
- 确保深度克隆的完整性
- 包含空值检查
- 保持与现有克隆逻辑一致

### 5. ApiUsageExample.java 修改

**测试XML更新**：
```xml
<step name='test_step' description='测试步骤' api='api1'>
  <prompts>
    <prompt name='test_prompt' role='user' api='api1'>测试内容</prompt>
  </prompts>
</step>
```

**测试输出增强**：
```java
// 测试step api属性
System.out.println("Step API: " + step.getApi());
// 测试prompt api属性  
System.out.println("Prompt API: " + prompt.getApi());
```

## 技术规范遵循

### 1. 参数类创建规范
- ✅ 类名明确表示用途
- ✅ 包含详细的中文注释说明
- ✅ 保持与项目现有风格一致
- ✅ 提供完整的getter/setter方法

### 2. 代码重构规范
- ✅ 遵循单一职责原则
- ✅ 保持向后兼容性
- ✅ 采用静态方法设计模式（ModeParser）
- ✅ 保留委托方法确保兼容性

### 3. API设计规范
- ✅ 支持XML属性解析
- ✅ 提供完整的属性支持
- ✅ 保持与现有API配置一致

## 支持的XML格式

### Step级别的API引用
```xml
<step name="step_name" api="api_reference_name">
  <!-- step content -->
</step>
```

### Prompt级别的API引用  
```xml
<prompt name="prompt_name" role="user" api="api_reference_name">
  <!-- prompt content -->
</prompt>
```

## 使用示例

### 1. 创建带API引用的Step
```java
Step step = new Step("test_step", "测试步骤", prompts);
step.setApi("api1");
String apiRef = step.getApi(); // 获取API引用
```

### 2. 创建带API引用的Prompt
```java
Prompt prompt = new Prompt("test_prompt", "user", "描述", null, null, null, "内容");
prompt.setApi("api1");
String apiRef = prompt.getApi(); // 获取API引用
```

### 3. XML解析
```java
// XML会自动解析api属性并设置到相应对象
Mode mode = ModeParser.parseMode(xmlElement);
Step step = mode.getSteps().get(0);
String stepApi = step.getApi(); // 自动解析的API引用
```

## 验证结果

- ✅ 所有类编译无语法错误
- ✅ XML解析功能正常工作
- ✅ 克隆功能包含新属性
- ✅ 测试示例正确运行
- ✅ 向后兼容性保持

## 总结

成功为 Step 和 Prompt 类添加了 api 属性支持，包括：

1. **完整的属性支持**：getter/setter方法和详细注释
2. **XML解析集成**：ModeParser自动解析api属性
3. **克隆功能完整**：深度克隆包含新属性
4. **测试覆盖**：ApiUsageExample包含新属性测试
5. **规范遵循**：严格遵循项目开发规范

此次修改增强了AI模式配置的灵活性，允许Step和Prompt级别的API引用，为后续的API调用功能提供了基础支持。