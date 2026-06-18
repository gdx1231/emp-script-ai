package com.gdxsoft.ai.test;

import java.io.FileWriter;
import java.io.PrintWriter;

import org.json.JSONObject;

import com.gdxsoft.easyweb.conf.ConnectionConfigs;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 从数据库读取 AI_PROVIDER* 表配置，生成 ai_settings.json
 * <p>
 * 数据库连接配置从 classpath 下的 ewa_conf.xml 或 ewa_conf_console.xml 读取（框架自动加载）。
 * <p>
 * 用法：
 * <pre>
 * java -cp ... com.gdxsoft.ai.test.GenerateAiSettings [dbConfigName] [outputPath]
 * </pre>
 * <p>
 * 参数说明：
 * <ul>
 * <li>dbConfigName: 数据库配置名称（ewa_conf.xml 中 &lt;database name="..."&gt; 的值），默认 "sqlserver"</li>
 * <li>outputPath: 输出文件路径，默认 "src/test/resources/ai_settings.json"</li>
 * </ul>
 * <p>
 * ewa_conf.xml 配置示例：
 * <pre>
 * &lt;databases&gt;
 *   &lt;database name="sqlserver" type="MSSQL" connectionString="jdbc/mssql" schemaName="dbo"&gt;
 *     &lt;pool username="sa" password="xxx"
 *       driverClassName="com.microsoft.sqlserver.jdbc.SQLServerDriver"
 *       url="jdbc:sqlserver://localhost:1433;DatabaseName=OneWorld"&gt;
 *     &lt;/pool&gt;
 *   &lt;/database&gt;
 * &lt;/databases&gt;
 * </pre>
 */
public class GenerateAiSettings {

    private static final String DEFAULT_DB_CONFIG = "sqlserver";
    private static final String DEFAULT_OUTPUT_PATH = "src/test/resources/ai_settings.json";

    public static void main(String[] args) {
        String dbConfigName = args.length > 0 ? args[0] : DEFAULT_DB_CONFIG;
        String outputPath = args.length > 1 ? args[1] : DEFAULT_OUTPUT_PATH;

        try {
            // 初始化框架配置（加载 ewa_conf.xml）
            System.out.println("正在加载 ewa_conf.xml 配置...");
            ConnectionConfigs configs = ConnectionConfigs.instance();
            if (configs == null || configs.isEmpty()) {
                throw new RuntimeException("未找到数据库配置，请确保 classpath 下有 ewa_conf.xml 或 ewa_conf_console.xml");
            }
            System.out.println("已加载 " + configs.size() + " 个数据库配置");
            
            // 检查指定的配置是否存在
            if (!configs.containsKey(dbConfigName)) {
                System.err.println("未找到数据库配置: " + dbConfigName);
                System.err.println("可用配置: " + configs.keySet());
                System.exit(1);
            }

            JSONObject settings = generateSettings(dbConfigName);
            writeToFile(settings, outputPath);
            System.out.println("已生成配置到: " + outputPath);
            System.out.println("共 " + settings.length() + " 个 provider");
        } catch (Exception e) {
            System.err.println("生成失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 从数据库生成配置
     */
    public static JSONObject generateSettings(String dbConfigName) throws Exception {
        JSONObject settings = new JSONObject();
        settings.put("_comment", "此文件由 GenerateAiSettings 从数据库自动生成");

        RequestValue rv = new RequestValue();

        // 查询所有 USED 状态的 provider
        String sqlProviders = "SELECT DISTINCT AP_CODE FROM AI_PROVIDER WHERE AP_STATUS = 'USED' ORDER BY AP_CODE";
        DTTable tbProviders = DTTable.getJdbcTable(sqlProviders, dbConfigName, rv);

        if (tbProviders == null || tbProviders.getCount() == 0) {
            System.out.println("未找到 USED 状态的 provider");
            return settings;
        }

        for (int i = 0; i < tbProviders.getCount(); i++) {
            String providerCode = tbProviders.getCell(i, "AP_CODE").toString();
            String providerKey = providerCode.toLowerCase();

            try {
                JSONObject providerConfig = getProviderConfig(dbConfigName, rv, providerCode);
                if (providerConfig != null) {
                    settings.put(providerKey, providerConfig);
                    System.out.println("已添加: " + providerKey + " (model=" + providerConfig.optString("model") + ")");
                }
            } catch (Exception e) {
                System.err.println("处理 " + providerCode + " 失败: " + e.getMessage());
            }
        }

        return settings;
    }

    /**
     * 获取单个 provider 的配置
     */
    private static JSONObject getProviderConfig(String dbConfigName, RequestValue rv, String providerCode) throws Exception {
        // 查询 API URL 和 Key (取最新的 USED 记录)
        rv.addOrUpdateValue("provider", providerCode);
        String sqlUrl = "SELECT TOP 1 APU_URL, APU_KEY FROM AI_PROVIDER_URL " +
                "WHERE AP_CODE = @provider AND APU_STATUS = 'USED' " +
                "ORDER BY APU_MDATE DESC";
        DTTable tbUrl = DTTable.getJdbcTable(sqlUrl, dbConfigName, rv);

        if (tbUrl == null || tbUrl.getCount() == 0) {
            System.out.println("  跳过 " + providerCode + ": 未找到 API URL 配置");
            return null;
        }

        String apiUrl = tbUrl.getCell(0, "APU_URL").toString();
        String apiKey = tbUrl.getCell(0, "APU_KEY").toString();

        // 查询默认模型 (取第一个 USED 的模型)
        String sqlModel = "SELECT TOP 1 APM_CODE FROM AI_PROVIDER_MODEL " +
                "WHERE AP_CODE = @provider AND APM_STATUS = 'USED' " +
                "ORDER BY APM_CODE";
        DTTable tbModel = DTTable.getJdbcTable(sqlModel, dbConfigName, rv);

        String model = "";
        if (tbModel != null && tbModel.getCount() > 0) {
            model = tbModel.getCell(0, "APM_CODE").toString();
        }

        // 构建配置（输出完整 Key，用于实际测试）
        JSONObject config = new JSONObject();
        config.put("api_url", apiUrl);
        config.put("api_key", apiKey);
        config.put("model", model);

        return config;
    }

    /**
     * 写入文件
     */
    private static void writeToFile(JSONObject settings, String outputPath) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println(settings.toString(2));
        }
    }

    /**
     * 生成完整 Key 的版本 (用于手动复制)
     */
    public static JSONObject generateSettingsWithFullKey(String dbConfigName) throws Exception {
        JSONObject settings = generateSettings(dbConfigName);

        // 重新查询完整 Key
        RequestValue rv = new RequestValue();
        for (String providerKey : settings.keySet()) {
            if (providerKey.startsWith("_")) continue;

            String providerCode = providerKey.toUpperCase();
            rv.addOrUpdateValue("provider", providerCode);

            String sqlUrl = "SELECT TOP 1 APU_KEY FROM AI_PROVIDER_URL " +
                    "WHERE AP_CODE = @provider AND APU_STATUS = 'USED' " +
                    "ORDER BY APU_MDATE DESC";
            DTTable tbUrl = DTTable.getJdbcTable(sqlUrl, dbConfigName, rv);

            if (tbUrl != null && tbUrl.getCount() > 0) {
                String fullKey = tbUrl.getCell(0, "APU_KEY").toString();
                settings.getJSONObject(providerKey).put("api_key", fullKey);
            }
        }

        return settings;
    }
}
