package com.gdxsoft.ai.modes;

import java.util.List;
import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Mode对象的XML解析器类
 * 包含所有与XML解析相关的静态方法
 * 
 * @author PF2023项目组
 * @since 2025-08-23
 */
public class ModeParser {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ModeParser.class);

    /**
     * 私有构造函数，防止实例化
     */
    private ModeParser() {
    }

    /**
     * 解析XML <mode> 元素为Mode实例
     * 
     * @param root mode元素
     * @return Mode对象
     */
    public static Mode parseMode(Element root) {
        String modeName = root.getAttribute("name");
        String modeDescription = root.getAttribute("description");
        String temperatureAttr = root.getAttribute("temperature");
        String topPAttr = root.getAttribute("topP");
        String thinkingAttr = root.getAttribute("thinking");

        // Parse steps
        List<Step> steps = new ArrayList<>();
        NodeList stepNodes = root.getElementsByTagName("step");
        for (int i = 0; i < stepNodes.getLength(); i++) {
            Element stepElement = (Element) stepNodes.item(i);
            String stepName = stepElement.getAttribute("name");
            String stepDescription = stepElement.getAttribute("description");
            String stepAction = stepElement.getAttribute("action");
            String stepApi = stepElement.getAttribute("api");
            String actionSqlRef = stepElement.getAttribute("actionSqlRef");
            String stepStreamAttr = stepElement.getAttribute("stream");
            boolean stepStream = true; // default true
            if (stepStreamAttr != null && stepStreamAttr.trim().length() > 0) {
                stepStream = Boolean.parseBoolean(stepStreamAttr.trim());
            }

            // Parse prompts within step
            List<Prompt> prompts = new ArrayList<>();
            NodeList promptsNode = stepElement.getElementsByTagName("prompts");
            if (promptsNode.getLength() > 0) {
                prompts = parsePrompts((Element) promptsNode.item(0));
            }
            Step step;
            if (stepAction != null && stepAction.length() > 0) {
                step = new Step(stepName, stepDescription, prompts, stepAction);
            } else {
                step = new Step(stepName, stepDescription, prompts);
            }
            step.setStream(stepStream);
            if (actionSqlRef != null && actionSqlRef.trim().length() > 0) {
                step.setActionSqlRef(actionSqlRef.trim());
            }
            if (stepApi != null && stepApi.trim().length() > 0) {
                step.setApi(stepApi.trim());
            }
            steps.add(step);
        }

        // Parse SQL queries
        List<SqlQuery> sqlQueries = new ArrayList<>();
        NodeList sqlsNode = root.getElementsByTagName("sqls");
        if (sqlsNode.getLength() > 0) {
            sqlQueries = parseSqlQueries((Element) sqlsNode.item(0));
        }

        // Parse actions
        List<Action> actions = new ArrayList<>();
        NodeList actionsNodes = root.getElementsByTagName("actions");
        if (actionsNodes.getLength() > 0) {
            Element actionsElement = (Element) actionsNodes.item(0);
            NodeList actionNodes = actionsElement.getElementsByTagName("action");
            for (int i = 0; i < actionNodes.getLength(); i++) {
                Element actionElement = (Element) actionNodes.item(i);
                String actionName = actionElement.getAttribute("name");
                String actionDescription = actionElement.getAttribute("description");
                String className = actionElement.getAttribute("class");
                actions.add(new Action(actionName, actionDescription, className));
            }
        }

        // Parse APIs
        List<Api> apis = new ArrayList<>();
        NodeList apisNodes = root.getElementsByTagName("apis");
        if (apisNodes.getLength() > 0) {
            Element apisElement = (Element) apisNodes.item(0);
            NodeList apiNodes = apisElement.getElementsByTagName("api");
            for (int i = 0; i < apiNodes.getLength(); i++) {
                Element apiElement = (Element) apiNodes.item(i);
                Api api = parseApi(apiElement);
                apis.add(api);
            }
        }

        Mode mode = new Mode(modeName, modeDescription, steps, sqlQueries, actions, apis);
        if (temperatureAttr != null && temperatureAttr.trim().length() > 0) {
            try {
                mode.setTemperature(Double.parseDouble(temperatureAttr.trim()));
            } catch (NumberFormatException ex) {
                LOGGER.warn("Invalid temperature attribute: {}", temperatureAttr);
            }
        }
        if (topPAttr != null && topPAttr.trim().length() > 0) {
            try {
                mode.setTopP(Double.parseDouble(topPAttr.trim()));
            } catch (NumberFormatException ex) {
                LOGGER.warn("Invalid topP attribute: {}", topPAttr);
            }
        }
        if (thinkingAttr != null && thinkingAttr.trim().length() > 0) {
            mode.setThinking(Boolean.parseBoolean(thinkingAttr.trim()));
        }
        return mode;
    }

    /**
     * 解析提示列表
     * 
     * @param promptsElement prompts元素
     * @return 提示列表
     */
    public static List<Prompt> parsePrompts(Element promptsElement) {
        List<Prompt> prompts = new ArrayList<>();
        NodeList promptNodes = promptsElement.getElementsByTagName("prompt");
        for (int i = 0; i < promptNodes.getLength(); i++) {
            Element promptElement = (Element) promptNodes.item(i);
            Prompt prompt = parsePrompt(promptElement);
            prompts.add(prompt);
        }
        return prompts;
    }

    /**
     * 解析单个提示
     * 
     * @param promptElement prompt元素
     * @return Prompt对象
     */
    public static Prompt parsePrompt(Element promptElement) {
        String promptName = promptElement.getAttribute("name");
        String role = promptElement.getAttribute("role");
        String description = promptElement.getAttribute("description");
        String sqlRef = promptElement.getAttribute("sqlRef");
        String dataType = promptElement.getAttribute("dataType");
        String prefix = promptElement.getAttribute("prefix");
        String content = getElementContent(promptElement);

        String dataGroupField = promptElement.getAttribute("dataGroupField");
        String action = promptElement.getAttribute("action");
        String api = promptElement.getAttribute("api");
        String showInChatAttr = promptElement.getAttribute("showInChat");
        Prompt p = new Prompt(promptName, role, description, sqlRef, dataType, prefix, content, action);
        if (dataGroupField != null && dataGroupField.length() > 0) {
            p.setDataGroupField(dataGroupField);
        }
        if (showInChatAttr != null && showInChatAttr.trim().length() > 0) {
            p.setShowInChat(Boolean.parseBoolean(showInChatAttr.trim()));
        }
        if (api != null && api.trim().length() > 0) {
            p.setApi(api.trim());
        }
        return p;
    }

    /**
     * 解析单个SQL查询
     * 
     * @param sqlElement sql元素
     * @return SqlQuery对象
     */
    public static SqlQuery parseSqlQuery(Element sqlElement) {
        String sqlName = sqlElement.getAttribute("name");
        String sqlDescription = sqlElement.getAttribute("description");
        String sqlContent = getElementContent(sqlElement);
        return new SqlQuery(sqlName, sqlDescription, sqlContent);
    }

    /**
     * 解析SQL查询列表
     * 
     * @param sqlsElement sqls元素
     * @return SQL查询列表
     */
    public static List<SqlQuery> parseSqlQueries(Element sqlsElement) {
        List<SqlQuery> sqlQueries = new ArrayList<>();
        NodeList sqlNodes = sqlsElement.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElement = (Element) sqlNodes.item(i);
            SqlQuery sql = parseSqlQuery(sqlElement);
            sqlQueries.add(sql);
        }
        return sqlQueries;
    }

    /**
     * 获取元素内容的工具方法，处理CDATA
     * 
     * @param element 元素
     * @return 元素的文本内容
     */
    public static String getElementContent(Element element) {
        StringBuilder content = new StringBuilder();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.CDATA_SECTION_NODE || node.getNodeType() == Node.TEXT_NODE) {
                content.append(node.getTextContent().trim());
            }
        }
        return content.toString();
    }

    /**
     * 解析API元素
     * 
     * @param apiElement API元素
     * @return API对象
     */
    public static Api parseApi(Element apiElement) {
        String name = apiElement.getAttribute("name");
        String description = apiElement.getAttribute("description");
        String url = apiElement.getAttribute("url");
        String method = apiElement.getAttribute("method");
        String timeoutStr = apiElement.getAttribute("timeout");
        String refRequestStr = apiElement.getAttribute("refRequest");
        String parameters = apiElement.getAttribute("parameters");
        String key = apiElement.getAttribute("key");

        // 设置默认值
        if (method == null || method.trim().length() == 0) {
            method = "GET";
        }
        int timeout = 5000; // 默认5秒
        if (timeoutStr != null && timeoutStr.trim().length() > 0) {
            try {
                timeout = Integer.parseInt(timeoutStr.trim());
            } catch (NumberFormatException ex) {
                LOGGER.warn("Invalid timeout attribute: {}", timeoutStr);
            }
        }
        boolean refRequest = false;
        if (refRequestStr != null && refRequestStr.trim().length() > 0) {
            refRequest = Boolean.parseBoolean(refRequestStr.trim());
        }

        Api api = new Api(name, description, url);
        api.setMethod(method);
        api.setTimeout(timeout);
        api.setRefRequest(refRequest);
        api.setParameters(parameters);
        api.setKey(key);

        // 解析body元素
        NodeList bodyNodes = apiElement.getElementsByTagName("body");
        if (bodyNodes.getLength() > 0) {
            Element bodyElement = (Element) bodyNodes.item(0);
            String body = getElementContent(bodyElement);
            api.setBody(body);
        }

        // 解析headers元素
        NodeList headersNodes = apiElement.getElementsByTagName("headers");
        if (headersNodes.getLength() > 0) {
            Element headersElement = (Element) headersNodes.item(0);
            List<ApiHeader> headers = parseApiHeaders(headersElement);
            api.setHeaders(headers);
        }

        // 解析form元素
        NodeList formNodes = apiElement.getElementsByTagName("form");
        if (formNodes.getLength() > 0) {
            Element formElement = (Element) formNodes.item(0);
            List<ApiField> form = parseApiForm(formElement);
            api.setForm(form);
        }

        return api;
    }

    /**
     * 解析API请求头
     * 
     * @param headersElement headers元素
     * @return 请求头列表
     */
    public static List<ApiHeader> parseApiHeaders(Element headersElement) {
        List<ApiHeader> headers = new ArrayList<>();
        NodeList headerNodes = headersElement.getElementsByTagName("header");
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element headerElement = (Element) headerNodes.item(i);
            String name = headerElement.getAttribute("name");
            String value = headerElement.getAttribute("value");
            headers.add(new ApiHeader(name, value));
        }
        return headers;
    }

    /**
     * 解析API表单字段
     * 
     * @param formElement form元素
     * @return 表单字段列表
     */
    public static List<ApiField> parseApiForm(Element formElement) {
        List<ApiField> form = new ArrayList<>();
        NodeList fieldNodes = formElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            String name = fieldElement.getAttribute("name");
            String value = fieldElement.getAttribute("value");
            form.add(new ApiField(name, value));
        }
        return form;
    }
}