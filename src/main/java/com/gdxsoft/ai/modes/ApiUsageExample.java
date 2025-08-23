package com.gdxsoft.ai.modes;

import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.StringReader;
import org.xml.sax.InputSource;

/**
 * API功能使用示例类
 * 展示如何使用新增的API解析和处理功能
 * 
 * @author PF2023项目组
 * @since 2025-08-23
 */
public class ApiUsageExample {

    /**
     * 主方法，演示API功能的使用
     */
    public static void main(String[] args) {
        try {
            // 示例XML内容，包含API配置
            String xmlContent = "<mode name='test_mode' description='测试模式'>" +
                    "  <step name='test_step' description='测试步骤' api='api1'>" +
                    "    <prompts>" +
                    "      <prompt name='test_prompt' role='user' api='api1'>测试内容</prompt>" +
                    "    </prompts>" +
                    "  </step>" +
                    "  <apis>" +
                    "    <api name='api1' url='/back_admin/test/test.jsp' parameters='a=1&amp;b=2'" +
                    "         refRequest='true' key='' timeout='5000' method='post'>" +
                    "      <body><![CDATA[{\"test\":true, \"test2\":false}]]></body>" +
                    "      <headers>" +
                    "        <header name='Content-Type' value='application/json' />" +
                    "        <header name='Authorization' value='Bearer token123' />" +
                    "      </headers>" +
                    "      <form>" +
                    "        <field name='test' value='true' />" +
                    "        <field name='test2' value='false' />" +
                    "      </form>" +
                    "    </api>" +
                    "  </apis>" +
                    "</mode>";

            // 解析XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlContent)));
            Element root = document.getDocumentElement();

            // 使用ModeParser.parseMode解析
            Mode mode = ModeParser.parseMode(root);

            // 验证解析结果
            System.out.println("Mode Name: " + mode.getName());
            System.out.println("Mode Description: " + mode.getDescription());

            List<Api> apis = mode.getApis();
            System.out.println("APIs Count: " + (apis != null ? apis.size() : 0));

            // 测试step api属性
            if (mode.getSteps() != null && !mode.getSteps().isEmpty()) {
                Step step = mode.getSteps().get(0);
                System.out.println("\nStep API: " + step.getApi());

                // 测试prompt api属性
                if (step.getPrompts() != null && !step.getPrompts().isEmpty()) {
                    Prompt prompt = step.getPrompts().get(0);
                    System.out.println("Prompt API: " + prompt.getApi());
                }
            }

            if (apis != null && !apis.isEmpty()) {
                Api api = apis.get(0);
                System.out.println("\nAPI Details:");
                System.out.println("  Name: " + api.getName());
                System.out.println("  URL: " + api.getUrl());
                System.out.println("  Method: " + api.getMethod());
                System.out.println("  Timeout: " + api.getTimeout());
                System.out.println("  RefRequest: " + api.isRefRequest());
                System.out.println("  Parameters: " + api.getParameters());
                System.out.println("  Body: " + api.getBody());

                System.out.println("\nHeaders:");
                for (ApiHeader header : api.getHeaders()) {
                    System.out.println("    " + header.getName() + ": " + header.getValue());
                }

                System.out.println("\nForm Fields:");
                for (ApiField field : api.getForm()) {
                    System.out.println("    " + field.getName() + ": " + field.getValue());
                }
            }

            // 测试API查找功能
            Api foundApi = mode.getApi("api1");
            System.out.println("\nFound API: " + (foundApi != null ? foundApi.getName() : "null"));

            // 测试克隆功能
            Mode clonedMode = mode.cloneMode();
            System.out.println("\nCloned Mode APIs Count: " +
                    (clonedMode.getApis() != null ? clonedMode.getApis().size() : 0));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 演示如何创建API对象
     */
    public static void demonstrateApiCreation() {
        // 创建API对象
        Api api = new Api("testApi", "/api/test");
        api.setMethod("POST");
        api.setTimeout(10000);
        api.setRefRequest(true);
        api.setParameters("param1=value1&param2=value2");
        api.setBody("{\"message\": \"hello world\"}");

        // 添加请求头
        api.addHeader("Content-Type", "application/json");
        api.addHeader("Authorization", "Bearer token123");

        // 添加表单字段
        api.addField("username", "testuser");
        api.addField("password", "testpass");

        System.out.println("Created API: " + api);
        System.out.println("Headers count: " + api.getHeaders().size());
        System.out.println("Form fields count: " + api.getForm().size());
    }
}