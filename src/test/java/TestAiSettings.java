import org.json.JSONObject;

/**
 * 加载 test/resources/ai_settings.json 中的 AI 服务商配置。
 * <p>
 * ai_settings.json 通过 sqlcmd 从数据库生成：
 * <pre>
 * # 刷新配置（从数据库 AI_PROVIDER 表读取）
 * source scripts/refresh_ai_settings.sh
 * </pre>
 * <p>
 * 用法：
 * <pre>
 * // 获取某个 provider 的配置
 * JSONObject cfg = TestAiSettings.get("QWEN");
 * String apiUrl = cfg.getString("api_url");
 * String apiKey = cfg.getString("api_key");
 * String model  = cfg.getString("model");
 *
 * // 检查是否配置了 API Key
 * if (!TestAiSettings.isConfigured("QWEN")) {
 *     System.out.println("跳过测试：未配置 QWEN");
 *     return;
 * }
 *
 * // 便捷创建已配置的 RequestAI
 * IRequestAI req = TestAiSettings.createRequest("QWEN");
 * </pre>
 */
public class TestAiSettings {

    private static JSONObject settings;

    /**
     * 加载配置文件（仅加载一次，缓存结果）。
     */
    private static JSONObject load() {
        if (settings != null) {
            return settings;
        }
        try {
            java.net.URL url = TestAiSettings.class.getClassLoader().getResource("ai_settings.json");
            if (url == null) {
                System.err.println("ai_settings.json 未找到，请运行: source scripts/refresh_ai_settings.sh");
                settings = new JSONObject();
                return settings;
            }
            String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(url.toURI())),
                    java.nio.charset.StandardCharsets.UTF_8);
            settings = new JSONObject(json);
        } catch (Exception e) {
            System.err.println("加载 ai_settings.json 失败: " + e.getMessage());
            settings = new JSONObject();
        }
        return settings;
    }

    /**
     * 获取指定 provider 的配置。
     *
     * @param provider provider 名称（QWEN, GEMINI, DEEPSEEK 等）
     * @return 包含 api_url, api_key, model 的 JSONObject
     */
    public static JSONObject get(String provider) {
        JSONObject cfg = load().optJSONObject(provider);
        return cfg != null ? cfg : new JSONObject();
    }

    /**
     * 检查指定 provider 是否已配置 API Key。
     *
     * @param provider provider 名称
     * @return true 表示已配置，可以直接调用 API
     */
    public static boolean isConfigured(String provider) {
        JSONObject cfg = get(provider);
        String key = cfg.optString("api_key", "");
        return key != null && !key.isEmpty();
    }

    /**
     * 获取所有已配置的 provider 名称列表。
     */
    public static String[] getConfiguredProviders() {
        JSONObject all = load();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String key : all.keySet()) {
            if (isConfigured(key)) {
                list.add(key);
            }
        }
        return list.toArray(new String[0]);
    }

    /**
     * 便捷方法：创建已配置的 IRequestAI 实例。
     * 如果未配置则返回 null。
     */
    public static com.gdxsoft.ai.request.IRequestAI createRequest(String provider) {
        if (!isConfigured(provider)) {
            return null;
        }
        JSONObject cfg = get(provider);
        com.gdxsoft.ai.request.IRequestAI req = com.gdxsoft.ai.request.RequestAIFactory
                .createRequestAI(provider);
        req.initUrlAndKey(cfg.optString("api_url"), cfg.optString("api_key"));
        return req;
    }

    /**
     * 便捷方法：创建已配置的 IRequestData 实例并设置模型。
     * 如果未配置则返回 null。
     */
    public static com.gdxsoft.ai.request.IRequestData createRequestData(String provider) {
        if (!isConfigured(provider)) {
            return null;
        }
        JSONObject cfg = get(provider);
        String model = cfg.optString("model");
        com.gdxsoft.ai.request.IRequestData reqData = com.gdxsoft.ai.request.RequestDataFactory
                .createRequestData(provider);
        if (model != null && !model.isEmpty()) {
            reqData.model(model);
        }
        return reqData;
    }

    /**
     * 打印当前配置摘要。
     */
    public static void printSummary() {
        JSONObject all = load();
        System.out.println("=== AI Provider 配置 ===");
        for (String key : all.keySet()) {
            JSONObject cfg = all.getJSONObject(key);
            boolean configured = isConfigured(key);
            System.out.printf("  %-20s | %-6s | %s | %s%n",
                    key,
                    configured ? "✅" : "❌",
                    mask(cfg.optString("api_key", "")),
                    cfg.optString("model", "-"));
        }
        System.out.println("=======================");
    }

    private static String mask(String key) {
        if (key == null || key.isEmpty()) return "-";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
