import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;

/**
 * 测试所有已配置的 AI Provider。
 */
public class TestAllProviders {

    public static void main(String[] args) {
        String[] providers = TestAiSettings.getConfiguredProviders();
        
        if (providers.length == 0) {
            System.out.println("没有已配置的 provider，请运行: source scripts/refresh_ai_settings.sh");
            return;
        }

        System.out.println("共 " + providers.length + " 个 provider 需要测试\n");
        System.out.println("=".repeat(70));

        int passed = 0, failed = 0;

        for (String provider : providers) {
            System.out.print("Testing " + provider + " ... ");
            
            try {
                IRequestAI req = TestAiSettings.createRequest(provider);
                if (req == null) {
                    System.out.println("SKIP (no config)");
                    continue;
                }

                IRequestData reqData = TestAiSettings.createRequestData(provider);
                if (reqData == null) {
                    System.out.println("SKIP (no model)");
                    continue;
                }

                reqData.stream(false).addMessage("Say OK in one word", "user");

                String response = req.doPost(reqData);
                JSONObject json = req.extraceJson(response, true);

                if (json != null && json.optBoolean("RST", false) && json.has("content")) {
                    String content = json.getString("content").trim();
                    JSONObject usage = req.getTokensUsage();
                    String usageStr = "";
                    if (usage != null) {
                        usageStr = String.format(" [tokens: %d in / %d out / %d total]",
                                usage.optInt("prompt_tokens", 0),
                                usage.optInt("completion_tokens", 0),
                                usage.optInt("total_tokens", 0));
                    }
                    System.out.println("PASS" + usageStr + " => " + content);
                    passed++;
                } else {
                    System.out.println("FAIL (no content in response)");
                    System.out.println("  Raw: " + (response.length() > 200 ? response.substring(0, 200) : response));
                    failed++;
                }

            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
        }

        System.out.println("=".repeat(70));
        System.out.println("Result: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
