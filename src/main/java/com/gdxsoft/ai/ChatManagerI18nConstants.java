package com.gdxsoft.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * AI聊天管理器国际化字符串常量
 * 集中管理所有用户可见的国际化字符串，支持中英文切换
 * 
 * @author guolei
 */
public final class ChatManagerI18nConstants {

    /**
     * 国际化字符串常量映射
     * 存储格式：key -> [中文, 英文]
     */
    public static final Map<String, String[]> I18N_MESSAGES = new HashMap<String, String[]>() {
        private static final long serialVersionUID = 1L;
        {
            // ========== 日志信息类 ==========
            put("MODEL_REQUEST_PARAMS", new String[] { "模型请求参数：{}", "Model request parameters: {}" });
            put("MESSAGE_NOT_JSON", new String[] { "读取的消息不是JSON格式，忽略：", "Message is not in JSON format, ignoring: " });
            put("EXPORT_RESULT", new String[] { "导出结果：{}", "Export result: {}" });
            put("AI_CHAT_RECORD", new String[] { "AI聊天记录：{}", "AI chat record: {}" });
            put("ACTION_LOADING", new String[] { "加载 actionName={}, 类名：{}", "Loading actionName={}, class: {}" });

            // ========== 错误信息类 ==========
            put("ERROR_NO_REQUEST_ID", new String[] { "无请求ID requestId，", "No request ID provided" });
            put("ERROR_NO_AI_PROVIDER", new String[] { "无AI提供商 ai_provider，", "No AI provider specified" });
            put("ERROR_NO_AI_MODEL", new String[] { "无AI模型 ai_model，", "No AI model specified" });
            put("ERROR_NO_AI_MODE", new String[] { "无AI模式mode", "No AI mode specified" });
            put("ERROR_MODE_NOT_FOUND", new String[] { "找不到模式：", "Mode not found: " });
            put("ERROR_STEP_NOT_FOUND", new String[] { "找不到步骤Step=：", "Step not found: " });
            put("ACTION_LOAD_FAILED", new String[] { "加载Action失败, {}", "Failed to load Action, {}" });
            put("ERROR_ACTION_LOAD_FAILED", new String[] { "加载Action失败：", "Failed to load Action: " });
            put("ERROR_API_URL_EMPTY", new String[] { "AI接口地址api_url不能为空", "AI API URL cannot be empty" });
            put("ERROR_MODEL_NOT_EXIST", new String[] { "模型不存在：{},供应商：{}", "Model does not exist: {}, Provider: {}" });
            put("ERROR_MODEL_OFFLINE_0", new String[] { "模型已下线0：{},供应商：{}", "Model is offline (0): {}, Provider: {}" });
            put("ERROR_MODEL_OFFLINE_1", new String[] { "模型已下线1：{},供应商：{}", "Model is offline (1): {}, Provider: {}" });
            put("ERROR_API_CONFIG_NOT_EXIST",
                    new String[] { "API配置不存在,供应商：{}", "API configuration does not exist, Provider: {}" });
            put("ERROR_GENERAL", new String[] { "错误：{}", "Error: {}" });

            // ========== 状态提示类 ==========
            put("ACTION_CREATING", new String[] { "正在创建中...", "Creating..." });
            put("SUCCESS_OK", new String[] { "OK", "OK" });
            put("ERROR_AI_CHAT_CREATE_FAILED", new String[] { "AI聊天创建失败：{}", "AI chat creation failed: {}" });

            put("ERROR_API_NOT_FOUND", new String[] { "API未找到：{}", "API not found: {}" });

            // ========== STT 错误信息类 ==========
            put("ERROR_STT_NO_AUDIO",
                    new String[] { "STT请求缺少音频源", "STT request has no audio source" });
            put("ERROR_STT_PROVIDER_NOT_FOUND",
                    new String[] { "未找到STT提供商：{}", "STT provider not found: {}" });
            put("ERROR_STT_AUDIO_TOO_LARGE",
                    new String[] { "音频超过最大限制 {} 字节", "Audio exceeds limit of {} bytes" });
            put("ERROR_STT_UNSUPPORTED_FORMAT",
                    new String[] { "不支持的音频格式：{}", "Unsupported audio format: {}" });
            put("ERROR_STT_HTTP_ERROR",
                    new String[] { "STT HTTP错误 {}: {}", "STT HTTP error {}: {}" });
            put("ERROR_STT_RESPONSE_PARSE",
                    new String[] { "STT响应解析失败：{}", "STT response parse failed: {}" });
            put("ERROR_STT_NO_API_KEY",
                    new String[] { "STT提供商 {} 未配置API Key", "STT provider {} has no API key" });
            put("ERROR_STT_AZURE_REGION_MISSING",
                    new String[] { "Azure STT缺少 region 配置", "Azure STT requires region config" });

            // ========== IMG (Image Generation) 错误信息类 ==========
            put("ERROR_IMG_PROVIDER_NOT_FOUND",
                    new String[] { "未找到图片生成提供商：{}", "Image generation provider not found: {}" });
            put("ERROR_IMG_NO_API_KEY",
                    new String[] { "图片生成提供商 {} 未配置API Key", "Image generation provider {} has no API key" });
            put("ERROR_IMG_NO_PROMPT",
                    new String[] { "图片生成缺少提示词(prompt)", "Image generation request has no prompt" });
            put("ERROR_IMG_HTTP_ERROR",
                    new String[] { "图片生成HTTP错误 {}: {}", "Image generation HTTP error {}: {}" });
            put("ERROR_IMG_RESPONSE_PARSE",
                    new String[] { "图片生成响应解析失败：{}", "Image generation response parse failed: {}" });
        }
    };

    // ========== 常量键名定义 ==========

    /** 日志信息类常量 */
    public static final class LogMessages {
        public static final String MODEL_REQUEST_PARAMS = "MODEL_REQUEST_PARAMS";
        public static final String MESSAGE_NOT_JSON = "MESSAGE_NOT_JSON";
        public static final String EXPORT_RESULT = "EXPORT_RESULT";
        public static final String AI_CHAT_RECORD = "AI_CHAT_RECORD";
        public static final String ACTION_LOADING = "ACTION_LOADING";
    }

    /** 错误信息类常量 */
    public static final class ErrorMessages {
        public static final String ERROR_NO_REQUEST_ID = "ERROR_NO_REQUEST_ID";
        public static final String ERROR_NO_AI_PROVIDER = "ERROR_NO_AI_PROVIDER";
        public static final String ERROR_NO_AI_MODEL = "ERROR_NO_AI_MODEL";
        public static final String ERROR_NO_AI_MODE = "ERROR_NO_AI_MODE";
        public static final String ERROR_MODE_NOT_FOUND = "ERROR_MODE_NOT_FOUND";
        public static final String ERROR_STEP_NOT_FOUND = "ERROR_STEP_NOT_FOUND";
        public static final String ACTION_LOAD_FAILED = "ACTION_LOAD_FAILED";
        public static final String ERROR_ACTION_LOAD_FAILED = "ERROR_ACTION_LOAD_FAILED";
        public static final String ERROR_API_URL_EMPTY = "ERROR_API_URL_EMPTY";
        public static final String ERROR_MODEL_NOT_EXIST = "ERROR_MODEL_NOT_EXIST";
        public static final String ERROR_MODEL_OFFLINE_0 = "ERROR_MODEL_OFFLINE_0";
        public static final String ERROR_MODEL_OFFLINE_1 = "ERROR_MODEL_OFFLINE_1";
        public static final String ERROR_API_CONFIG_NOT_EXIST = "ERROR_API_CONFIG_NOT_EXIST";
        public static final String ERROR_GENERAL = "ERROR_GENERAL";
        public static final String ERROR_AI_CHAT_CREATE_FAILED = "ERROR_AI_CHAT_CREATE_FAILED";
        public static final String ERROR_API_NOT_FOUND = "ERROR_API_NOT_FOUND";
    }

    /** 状态提示类常量 */
    public static final class StatusMessages {
        public static final String ACTION_CREATING = "ACTION_CREATING";
        public static final String SUCCESS_OK = "SUCCESS_OK";
    }

    /** STT 错误信息类常量 */
    public static final class SttErrorMessages {
        public static final String ERROR_STT_NO_AUDIO = "ERROR_STT_NO_AUDIO";
        public static final String ERROR_STT_PROVIDER_NOT_FOUND = "ERROR_STT_PROVIDER_NOT_FOUND";
        public static final String ERROR_STT_AUDIO_TOO_LARGE = "ERROR_STT_AUDIO_TOO_LARGE";
        public static final String ERROR_STT_UNSUPPORTED_FORMAT = "ERROR_STT_UNSUPPORTED_FORMAT";
        public static final String ERROR_STT_HTTP_ERROR = "ERROR_STT_HTTP_ERROR";
        public static final String ERROR_STT_RESPONSE_PARSE = "ERROR_STT_RESPONSE_PARSE";
        public static final String ERROR_STT_NO_API_KEY = "ERROR_STT_NO_API_KEY";
        public static final String ERROR_STT_AZURE_REGION_MISSING = "ERROR_STT_AZURE_REGION_MISSING";
    }

    /** IMG (Image Generation) 错误信息类常量 */
    public static final class ImgErrorMessages {
        public static final String ERROR_IMG_PROVIDER_NOT_FOUND = "ERROR_IMG_PROVIDER_NOT_FOUND";
        public static final String ERROR_IMG_NO_API_KEY = "ERROR_IMG_NO_API_KEY";
        public static final String ERROR_IMG_NO_PROMPT = "ERROR_IMG_NO_PROMPT";
        public static final String ERROR_IMG_HTTP_ERROR = "ERROR_IMG_HTTP_ERROR";
        public static final String ERROR_IMG_RESPONSE_PARSE = "ERROR_IMG_RESPONSE_PARSE";
    }

    /**
     * 工具方法：获取国际化文本
     * 
     * @param key        文本键
     * @param useEnglish 是否使用英文
     * @param args       格式化参数
     * @return 格式化后的国际化文本
     */
    public static String getText(String key, boolean useEnglish, Object... args) {
        String[] messages = I18N_MESSAGES.get(key);
        if (messages == null || messages.length < 2) {
            // 如果找不到对应的文本，返回key本身
            return key;
        }

        String template = useEnglish ? messages[1] : messages[0];

        // 如果有参数，进行格式化
        if (args != null && args.length > 0) {
            // 使用简单的字符串替换，支持{}占位符
            String result = template;
            for (int i = 0; i < args.length; i++) {
                result = result.replaceFirst("\\{\\}", String.valueOf(args[i]));
            }
            return result;
        }

        return template;
    }

    /**
     * 私有构造函数，防止实例化
     */
    private ChatManagerI18nConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}