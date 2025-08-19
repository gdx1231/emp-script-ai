package com.gdxsoft.ai.providers.doubao;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * 豆包（Doubao/火山引擎）OpenAI 兼容聊天补全流式接口
 * 默认使用 ByteDance OpenAI-Compatible endpoint
 */
public class RequestAI extends RequestAIBase {
    // Doubao 的 OpenAI 兼容流式 API（火山引擎兼容模式）
    // 文档参考： https://www.volcengine.com/docs/82379/1298450 （若路径不同，可按实际调整）
    public static final String DEFAULT_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    public RequestAI() {
        this.providerType = ProviderType.DOUBAO;
    }


    /**
     * 解析豆包（OpenAI 兼容）SSE 数据行
     * 形如： data: {"choices":[{"delta":{"content":"..."},...}]}
     */
    @Override
    public JSONObject extraceJson(String line) {
        if (!line.startsWith("data:")) {
            return UJSon.rstFalse("没有data:的数据行，" + line);
        }
        String jsonData = line.substring(5).trim();
        if (jsonData.isEmpty()) {
            return UJSon.rstFalse("data:无数据，" + line);
        }
        if ("[DONE]".equals(jsonData)) {
            return UJSon.rstTrue("流结束标记");
        }
        try {
            JSONObject json = new JSONObject(jsonData);
            if (json.has("choices")) {
                JSONArray choices = json.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice0 = choices.getJSONObject(0);
                    if (choice0.has("delta")) {
                        JSONObject delta = choice0.getJSONObject("delta");
                        UJSon.rstSetTrue(delta, null);
                        return delta;
                    }
                }
            }
            return UJSon.rstFalse("无效数据，choices/delta 未找到，" + line);
        } catch (Exception e) {
            return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
        }
    }
}
