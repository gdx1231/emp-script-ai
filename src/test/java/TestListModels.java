import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestAI;

/**
 * 测试所有已配置的 AI Provider 的 listModels 功能。
 */
public class TestListModels {

    public static void main(String[] args) {
        String[] providers = TestAiSettings.getConfiguredProviders();

        if (providers.length == 0) {
            System.out.println("没有已配置的 provider，请运行: source scripts/refresh_ai_settings.sh");
            return;
        }

        System.out.println("共 " + providers.length + " 个 provider 需要测试\n");
        System.out.println("=".repeat(70));

        for (String provider : providers) {
            System.out.println("\n=== 测试 " + provider + " ===");

            try {
                IRequestAI req = TestAiSettings.createRequest(provider);
                if (req == null) {
                    System.out.println("跳过 (未配置)");
                    continue;
                }

                JSONObject result = req.listModels();

                if (result.optBoolean("RST")) {
                    System.out.println("✅ 成功获取模型列表");
                    JSONArray models = result.optJSONArray("data");
                    if (models != null && models.length() > 0) {
                        System.out.println("共 " + models.length() + " 个模型:");
                        for (int i = 0; i < models.length(); i++) {
                            JSONObject model = models.getJSONObject(i);
                            System.out.println("  - " + model.optString("id"));
                        }
                    } else {
                        System.out.println("模型列表为空");
                    }
                } else {
                    System.out.println("❌ 获取模型列表失败");
                    System.out.println("  原因: " + result.optString("ERR"));
                }

            } catch (Exception e) {
                System.out.println("❌ 测试 " + provider + " 时发生异常");
                System.out.println("  异常: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("测试完成");
    }
}
