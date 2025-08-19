package com.gdxsoft.ai.modes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.gdxsoft.easyweb.utils.UXml;
import com.gdxsoft.easyweb.utils.Utils;

public class Modes {
    private static final Logger LOGGER = Logger.getLogger(Modes.class.getName());
    private Document document;
    private static Map<String, Mode> MODES = new ConcurrentHashMap<String, Mode>();
    // cache md5 to avoid re-parse when xmlContent unchanged
    private static volatile Map<String, List<Mode>> XML_MD5 = new ConcurrentHashMap<>();

    public static Mode getMode(String name) {
        Mode m = MODES.get(name);
        return m == null ? null : m.cloneMode();
    }

    private String xmlMd5;

    public Modes() {

    }

    public List<Mode> loadModes(String xmlContent) throws Exception {
        this.xmlMd5 = Utils.md5(xmlContent);
        if (XML_MD5.containsKey(xmlMd5)) {
            // Return cached list when md5 unchanged
            return XML_MD5.get(xmlMd5);
        }

        this.document = UXml.asDocument(xmlContent);

        List<Mode> modes = new ArrayList<>();
        NodeList modeNodes = document.getElementsByTagName("mode");

        for (int i = 0; i < modeNodes.getLength(); i++) {
            Element modeElement = (Element) modeNodes.item(i);
            Mode mode = Mode.parseMode(modeElement);
            MODES.put(mode.getName(), mode);
            modes.add(mode);
            LOGGER.info("Mode: " + mode.getName());
        }
        XML_MD5.put(this.xmlMd5, modes);
        return modes;
    }

}