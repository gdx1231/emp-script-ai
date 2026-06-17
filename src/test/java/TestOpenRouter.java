import java.io.StringWriter;
import java.io.PrintWriter;

import org.json.JSONObject;

import com.gdxsoft.ai.providers.openrouter.RequestAI;
import com.gdxsoft.ai.providers.openrouter.RequestData;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;

/**
 * OpenRouter API 测试
 * 运行前需设置环境变量: OPENROUTER_API_KEY
 * 或直接在代码中通过参数传入
 */
public class TestOpenRouter {

	/**
	 * 从环境变量获取 API Key
	 */
	private static String getApiKey() {
		String key = System.getenv("OPENROUTER_API_KEY");
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalStateException("请设置环境变量 OPENROUTER_API_KEY");
		}
		return key;
	}

	public static void main(String[] args) {
		String apiKey = getApiKey();
		testNonStream(apiKey);
		testStream(apiKey);
		testThinking(apiKey);
	}

	/**
	 * 测试非流式请求
	 */
	public static void testNonStream(String apiKey) {
		System.out.println("===== 测试非流式请求 =====");

		// OpenRouter 使用 OpenAI 兼容格式，模型名格式: provider/model
		IRequestData reqData = new RequestData()
				.model("meta-llama/llama-3.1-8b-instruct")
				.stream(false)
				.temperature(0.7)
				.systemMessage("你是一个简洁的AI助手，用一句话回答问题。")
				.userMessage("请用一句话介绍Python编程语言");

		System.out.println("请求体: " + reqData.buildJson());

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("OPENROUTER").optString("api_url"), apiKey);

		try {
			String response = req.doPost(reqData);
			System.out.println("响应: " + response);

			// 提取内容
			JSONObject json = req.extraceJson(response, true);
			System.out.println("内容: " + json.optString("content", "无内容"));
		} catch (Exception e) {
			System.err.println("请求失败: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * 测试流式请求
	 */
	public static void testStream(String apiKey) {
		System.out.println("\n===== 测试流式请求 =====");

		IRequestData reqData = new RequestData()
				.model("meta-llama/llama-3.1-8b-instruct")
				.stream(true)
				.temperature(0.7)
				.systemMessage("你是一个简洁的AI助手。")
				.userMessage("请写一首关于秋天的短诗，4句即可");

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("OPENROUTER").optString("api_url"), apiKey);

		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);

		try {
			String fullText = req.doStream(reqData, writer);
			System.out.println("完整响应: " + fullText);
			System.out.println("Token 使用: " + req.getTokensUsage());
		} catch (Exception e) {
			System.err.println("流式请求失败: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * 测试思考模式（reasoning）
	 */
	public static void testThinking(String apiKey) {
		System.out.println("\n===== 测试思考模式 =====");

		// 使用 Nemotron 模型 + reasoning 参数
		IRequestData reqData = new RequestData()
				.model("nvidia/nemotron-3-super-120b-a12b:free")
				.stream(false)
				.thinking(true)
				.userMessage("一个农场有鸡和兔共35个头，94只脚。问鸡和兔各有多少只？请展示推理过程。");

		System.out.println("请求体: " + reqData.buildJson());

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("OPENROUTER").optString("api_url"), apiKey);

		try {
			String response = req.doPost(reqData);
			System.out.println("响应: " + response);

			// 提取内容
			JSONObject json = req.extraceJson(response, true);
			System.out.println("\n--- reasoning_content / reasoning (推理过程) ---");
			String reasoning = json.optString("reasoning_content", json.optString("reasoning", "无推理过程"));
			System.out.println(reasoning);
			System.out.println("\n--- content (最终答案) ---");
			System.out.println(json.optString("content", "无内容"));
		} catch (Exception e) {
			System.err.println("思考模式请求失败: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
