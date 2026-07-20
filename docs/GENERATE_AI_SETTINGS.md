# 从数据库生成 AI 配置

## 功能

从数据库的 `AI_PROVIDER*` 表读取配置，生成 `ai_settings.json` 文件，用于集成测试。

## 使用方法

### 1. 确保数据库配置正确

项目需要能够连接到包含 `AI_PROVIDER*` 表的数据库。数据库连接配置在 `src/main/resources/` 或项目配置文件中。

### 2. 运行生成工具

```bash
# 使用默认数据库配置，输出到 src/test/resources/ai_settings.json
mvn exec:java -Dexec.mainClass="com.gdxsoft.ai.test.GenerateAiSettings" -Dexec.classpathScope=test

# 指定数据库配置名称和输出路径
mvn exec:java -Dexec.mainClass="com.gdxsoft.ai.test.GenerateAiSettings" \
  -Dexec.args="yourDbConfigName path/to/output.json" \
  -Dexec.classpathScope=test
```

### 3. 生成的文件格式

```json
{
  "_comment": "此文件由 GenerateAiSettings 从数据库自动生成",
  "qwen": {
    "api_url": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    "api_key": "sk-****abcd",
    "model": "qwen-plus"
  },
  "deepseek": {
    "api_url": "https://api.deepseek.com/v1/chat/completions",
    "api_key": "sk-****efgh",
    "model": "deepseek-chat"
  }
}
```

**注意**：默认生成的 `api_key` 是脱敏的（只显示前4位和后4位）。如需完整 Key，请使用 `generateSettingsWithFullKey()` 方法。

### 4. 获取完整 API Key

如果需要完整的 API Key（用于实际测试），可以修改代码调用 `generateSettingsWithFullKey()` 方法，或手动从数据库查询：

```sql
SELECT AP_CODE, APU_URL, APU_KEY 
FROM AI_PROVIDER_URL 
WHERE APU_STATUS = 'USED' 
ORDER BY APU_MDATE DESC
```

## 数据库表结构

### AI_PROVIDER
- `AP_CODE` - 提供商代码（如 QWEN, DEEPSEEK）
- `AP_NAME` - 提供商名称
- `AP_STATUS` - 状态（USED 表示启用）

### AI_PROVIDER_MODEL
- `APM_CODE` - 模型代码（如 qwen-plus, deepseek-chat）
- `AP_CODE` - 关联的提供商代码
- `APM_STATUS` - 状态

### AI_PROVIDER_URL
- `AP_CODE` - 关联的提供商代码
- `APU_URL` - API 地址
- `APU_KEY` - API 密钥
- `APU_STATUS` - 状态
- `APU_MDATE` - 修改时间（用于排序，取最新配置）

## 集成测试使用

生成的 `ai_settings.json` 会被 `TestDatabase.java` 读取，用于集成测试时配置 HSQLDB 内存数据库。

运行集成测试：

```bash
mvn test -Dtest=IntegrationTest
```

测试会自动：
1. 启动 HSQLDB 内存数据库
2. 读取 `ai_settings.json` 配置
3. 创建必要的表结构
4. 运行测试用例
