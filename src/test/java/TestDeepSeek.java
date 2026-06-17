import java.io.StringWriter;
import java.io.PrintWriter;

import org.json.JSONObject;

import com.gdxsoft.ai.providers.deepseek.RequestAI;
import com.gdxsoft.ai.providers.deepseek.RequestData;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;

/**
 * DeepSeek API 测试
 * 运行前需设置环境变量: DEEPSEEK_API_KEY
 */
public class TestDeepSeek {

	/**
	 * 从环境变量获取 API Key
	 */
	private static String getApiKey() {
		String key = System.getenv("DEEPSEEK_API_KEY");
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalStateException("请设置环境变量 DEEPSEEK_API_KEY");
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

		// 构建请求数据
		IRequestData reqData = new RequestData()
				.model("deepseek-chat")
				.stream(false)
				.temperature(0.7)
				.systemMessage("你是一个简洁的AI助手，用一句话回答问题。")
				.userMessage("请用一句话介绍Java编程语言");

		System.out.println("请求体: " + reqData.buildJson());

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("DEEPSEEK").optString("api_url"), apiKey);

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

		// 构建请求数据
		IRequestData reqData = new RequestData()
				.model("deepseek-chat")
				.stream(true)
				.temperature(0.7)
				.systemMessage("你是一个简洁的AI助手。")
				.userMessage("请写一首关于春天的短诗，4句即可");

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("DEEPSEEK").optString("api_url"), apiKey);

		// 输出流
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
	 * 测试思考模式（deepseek-reasoner）
	 */
	public static void testThinking(String apiKey) {
		System.out.println("\n===== 测试思考模式 =====");

		// 使用 deepseek-reasoner 模型，启用 thinking
		IRequestData reqData = new RequestData()
				.model("deepseek-reasoner")
				.stream(false)
				.thinking(true)
				.userMessage("一个农场有鸡和兔共35个头，94只脚。问鸡和兔各有多少只？请展示推理过程。");

		System.out.println("请求体: " + reqData.buildJson());

		// 创建请求实例
		IRequestAI req = new RequestAI();
		req.initUrlAndKey(TestAiSettings.get("DEEPSEEK").optString("api_url"), apiKey);

		try {
			String response = req.doPost(reqData);
			System.out.println("响应: " + response);

			// 提取内容
			JSONObject json = req.extraceJson(response, true);
			System.out.println("\n--- reasoning_content (推理过程) ---");
			System.out.println(json.optString("reasoning_content", "无推理过程"));
			System.out.println("\n--- content (最终答案) ---");
			System.out.println(json.optString("content", "无内容"));
		} catch (Exception e) {
			System.err.println("思考模式请求失败: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
