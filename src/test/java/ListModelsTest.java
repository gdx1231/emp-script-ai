import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.request.IRequestAI;

/**
 * 测试 listModels 功能。
 */
public class ListModelsTest {

    @Test
    public void testListModels() throws Exception {
        String[] providers = TestAiSettings.getConfiguredProviders();

        if (providers.length == 0) {
            System.out.println("没有已配置的 provider，跳过测试");
            return;
        }

        System.out.println("共 " + providers.length + " 个 provider 需要测试\n");

        for (String provider : providers) {
            System.out.println("\n=== 测试 " + provider + " ===");

            IRequestAI req = TestAiSettings.createRequest(provider);
            if (req == null) {
                System.out.println("跳过 (未配置)");
                continue;
            }

            JSONObject result = req.listModels();

            System.out.println("结果: " + result.toString(2));

            // 我们只验证调用成功，不强制要求每个 provider 都支持 listModels
            assertTrue(result != null, "返回结果不应为 null");
        }
    }
}
