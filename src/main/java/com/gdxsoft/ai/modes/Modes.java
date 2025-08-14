package com.gdxsoft.ai.modes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.gdxsoft.easyweb.utils.UXml;

public class Modes {
    private static final Logger LOGGER = Logger.getLogger(Modes.class.getName());
    private Document document;
    private static Map<String, Mode> MODES = new ConcurrentHashMap<String, Mode>();

    public static Mode getMode(String name) {
        return MODES.get(name);
    }

    public Modes(String xmlContent) throws Exception {
        this.document = UXml.asDocument(xmlContent);
    }

    public List<Mode> loadModes() {
        List<Mode> modes = new ArrayList<>();
        NodeList modeNodes = document.getElementsByTagName("mode");
        for (int i = 0; i < modeNodes.getLength(); i++) {
            Element modeElement = (Element) modeNodes.item(i);
            Mode mode = parseMode(modeElement);
            MODES.put(mode.getName(), mode);
            modes.add(mode);
            LOGGER.info("Mode: " + mode.getName());
        }
        return modes;
    }

    private List<Prompt> parsePrompts(Element promptsElement) {
        List<Prompt> prompts = new ArrayList<>();
        NodeList promptNodes = promptsElement.getElementsByTagName("prompt");
        for (int i = 0; i < promptNodes.getLength(); i++) {
            Element promptElement = (Element) promptNodes.item(i);
            Prompt prompt = parsePrompt(promptElement);
            prompts.add(prompt);
        }

        return prompts;
    }

    private Prompt parsePrompt(Element promptElement) {
        String promptName = promptElement.getAttribute("name");
        String role = promptElement.getAttribute("role");
        String description = promptElement.getAttribute("description");
        String sqlRef = promptElement.getAttribute("sqlRef");
        String dataType = promptElement.getAttribute("dataType");
        String prefix = promptElement.getAttribute("prefix");
        String content = getElementContent(promptElement);

        String dataGroupField = promptElement.getAttribute("dataGroupField");
        Prompt p = new Prompt(promptName, role, description, sqlRef, dataType, prefix, content);
        if (dataGroupField != null && dataGroupField.length() > 0) {
            p.setDataGroupField(dataGroupField);
        }
        return p;
    }

    private SqlQuery parseSqlQuery(Element sqlElement) {
        String sqlName = sqlElement.getAttribute("name");
        String sqlDescription = sqlElement.getAttribute("description");
        String sqlContent = getElementContent(sqlElement);

        return new SqlQuery(sqlName, sqlDescription, sqlContent);
    }

    private List<SqlQuery> parseSqlQueries(Element sqlsElement) {
        List<SqlQuery> sqlQueries = new ArrayList<>();
        NodeList sqlNodes = sqlsElement.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElement = (Element) sqlNodes.item(i);
            SqlQuery sql = parseSqlQuery(sqlElement);
            sqlQueries.add(sql);
        }

        return sqlQueries;
    }

    // Method to parse the XML and return a Mode object
    private Mode parseMode(Element root) {
        String modeName = root.getAttribute("name");
        String modeDescription = root.getAttribute("description");

        // Parse steps
        List<Step> steps = new ArrayList<>();
        NodeList stepNodes = root.getElementsByTagName("step");
        for (int i = 0; i < stepNodes.getLength(); i++) {
            Element stepElement = (Element) stepNodes.item(i);
            String stepName = stepElement.getAttribute("name");
            String stepDescription = stepElement.getAttribute("description");
            String stepAction = stepElement.getAttribute("action");

            // Parse prompts within step
            List<Prompt> prompts = new ArrayList<>();
            NodeList promptsNode = stepElement.getElementsByTagName("prompts");
            if (promptsNode.getLength() > 0) {
                prompts = parsePrompts((Element) promptsNode.item(0));
            }
            if (stepAction != null && stepAction.length() > 0) {
                steps.add(new Step(stepName, stepDescription, prompts, stepAction));
            } else {
                steps.add(new Step(stepName, stepDescription, prompts));
            }
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

        return new Mode(modeName, modeDescription, steps, sqlQueries, actions);
    }

    // Helper method to get content of an element, handling CDATA
    private String getElementContent(Element element) {
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

}