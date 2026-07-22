package com.gdxsoft.ai.modes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.gdxsoft.easyweb.utils.UXml;
import com.gdxsoft.easyweb.utils.Utils;

/**
 * 模式集合管理，负责加载与缓存 <mode> 定义。
 * <p>
 * Modes manager that loads and caches <mode> definitions.
 */
public class Modes {
    private static final Logger LOGGER = Logger.getLogger(Modes.class.getName());
    private static final Map<String, Mode> MODES = new ConcurrentHashMap<String, Mode>();
    private static final Map<String, List<Mode>> XML_MD5 = new ConcurrentHashMap<>();

    /**
     * 获取指定名称的模式（返回克隆体以避免外部修改缓存原件）。
     * <p>
     * Get a mode by name (returns a clone to prevent external mutation of cache).
     */
    public static Mode getMode(String name) {
        Mode m = MODES.get(name);
        return m == null ? null : m.cloneMode();
    }

    private Document document;
    private String xmlMd5;

    /**
     * 从 XML 文本中加载所有 <mode> 定义，按 name 放入缓存并返回列表。
     * <p>
     * Load all <mode> definitions from XML text, cache by name and return the list.
     */
    public List<Mode> loadModes(String xmlContent) throws Exception {
        this.xmlMd5 = Utils.md5(xmlContent);
        if (XML_MD5.containsKey(xmlMd5)) {
            // Return cached list when md5 unchanged
            return XML_MD5.get(xmlMd5);
        }

        this.document = UXml.asDocument(xmlContent);

        // 解析 <common><apis> 中的公共 API 定义
        List<Api> commonApis = parseCommonApis(this.document);

        List<Mode> modes = new ArrayList<>();
        NodeList modeNodes = document.getElementsByTagName("mode");

        for (int i = 0; i < modeNodes.getLength(); i++) {
            Element modeElement = (Element) modeNodes.item(i);
            Mode mode = Mode.parseMode(modeElement);
            // 合并公共 API：mode 自身的 apis 优先，common 中同名（忽略大小写）的 API 被抛弃
            for (Api commonApi : commonApis) {
                if (mode.getApi(commonApi.getName()) == null) {
                    mode.getApis().add(commonApi);
                }
            }
            MODES.put(mode.getName(), mode);
            modes.add(mode);
            LOGGER.info("Mode: " + mode.getName());
        }
        XML_MD5.put(this.xmlMd5, modes);
        return modes;
    }

    /**
     * 解析 <common> 下的公共 <api>/<tool> 定义列表（同名时 tool 整体覆盖 api）。
     * <p>
     * Parse common <api>/<tool> definitions under <common>.
     */
    private static List<Api> parseCommonApis(Document document) {
        List<Api> commonApis = new ArrayList<>();
        NodeList commonNodes = document.getElementsByTagName("common");
        if (commonNodes.getLength() == 0) {
            return commonApis;
        }
        Element commonElement = (Element) commonNodes.item(0);
        Map<String, Integer> apiNameIndex = new HashMap<>();
        ModeParser.collectApis(commonElement, "apis", "api", commonApis, apiNameIndex, false);
        ModeParser.collectApis(commonElement, "tools", "tool", commonApis, apiNameIndex, true);
        return commonApis;
    }

}