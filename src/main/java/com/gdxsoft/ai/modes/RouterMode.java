package com.gdxsoft.ai.modes;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 表示一个路由声明（<routerMode>），显式列出 mode=auto 可路由到的候选 mode。
 * <p>
 * Represents a routing declaration (<routerMode>) listing candidate modes that
 * {@code mode=auto} may route to.
 * <p>
 * 示例：
 * <pre>
 * &lt;routerMode name="auto" default="chat"&gt;
 *     &lt;route&gt;chat&lt;/route&gt;
 *     &lt;route&gt;translate&lt;/route&gt;
 * &lt;/routerMode&gt;
 * </pre>
 */
public class RouterMode {
    /** 路由名称，对应请求参数 mode=<name>（如 name="auto"） */
    private String name;
    /** 分类失败/返回无效 mode 时兜底的默认 mode 名（可选） */
    private String defaultMode;
    /** 候选 mode 名列表（&lt;route&gt; 子元素） */
    private List<String> routes = new ArrayList<>();
    /** 分类失败且无任何兜底时的用户可见提醒词（&lt;reminder&gt; 子元素，可选） */
    private String reminder;
    /** &lt;reminder&gt; 引用的共享 API/Tool 名称（api/tool 属性，可选），非空时调用 API 的结果作为提醒词 */
    private String reminderApi;
    /** 可用的共享 API 定义（来自 &lt;common&gt;，用于解析 reminder 的 api 引用） */
    private List<Api> apis = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public List<String> getRoutes() {
        return routes;
    }

    public void setRoutes(List<String> routes) {
        this.routes = routes != null ? routes : new ArrayList<>();
    }

    public String getReminder() {
        return reminder;
    }

    public void setReminder(String reminder) {
        this.reminder = reminder;
    }

    public String getReminderApi() {
        return reminderApi;
    }

    public void setReminderApi(String reminderApi) {
        this.reminderApi = reminderApi;
    }

    public List<Api> getApis() {
        return apis;
    }

    public void setApis(List<Api> apis) {
        this.apis = apis != null ? apis : new ArrayList<>();
    }

    /**
     * 按名称查找共享 API（忽略大小写），用于解析 &lt;reminder api="..."&gt;。
     *
     * @param apiName API 名称
     * @return 匹配的 API，未找到返回 null
     */
    public Api getApi(String apiName) {
        if (apis == null) {
            return null;
        }
        for (Api api : apis) {
            if (api.getName() != null && api.getName().equalsIgnoreCase(apiName)) {
                return api;
            }
        }
        return null;
    }

    /**
     * 返回副本：routes 拷贝一份新列表；apis 逐项深拷贝，避免外部修改缓存原件。
     *
     * @return 副本
     */
    public RouterMode cloneRouterMode() {
        RouterMode copy = new RouterMode();
        copy.setName(this.name);
        copy.setDefaultMode(this.defaultMode);
        copy.setRoutes(new ArrayList<>(this.routes));
        copy.setReminder(this.reminder);
        copy.setReminderApi(this.reminderApi);
        List<Api> apisCopy = new ArrayList<>();
        if (this.apis != null) {
            for (Api api : this.apis) {
                apisCopy.add(api.clone());
            }
        }
        copy.setApis(apisCopy);
        return copy;
    }

    /**
     * 解析 XML {@code <routerMode>} 元素。
     *
     * @param root       routerMode 元素
     * @param commonApis &lt;common&gt; 下的共享 API 定义，供 &lt;reminder api="..."&gt; 引用
     * @return RouterMode 对象
     */
    public static RouterMode parseRouterMode(Element root, List<Api> commonApis) {
        RouterMode rm = new RouterMode();
        String name = root.getAttribute("name");
        rm.setName(name != null && !name.trim().isEmpty() ? name.trim() : null);
        String def = root.getAttribute("default");
        rm.setDefaultMode(def != null && !def.trim().isEmpty() ? def.trim() : null);
        NodeList routeNodes = root.getElementsByTagName("route");
        for (int i = 0; i < routeNodes.getLength(); i++) {
            String value = routeNodes.item(i).getTextContent();
            if (value != null && !value.trim().isEmpty()) {
                rm.getRoutes().add(value.trim());
            }
        }
        NodeList reminderNodes = root.getElementsByTagName("reminder");
        if (reminderNodes.getLength() > 0) {
            Element reminderEl = (Element) reminderNodes.item(0);
            String value = reminderEl.getTextContent();
            if (value != null && !value.trim().isEmpty()) {
                rm.setReminder(value.trim());
            }
            String api = reminderEl.getAttribute("api");
            if (api == null || api.trim().isEmpty()) {
                api = reminderEl.getAttribute("tool");
            }
            if (api != null && !api.trim().isEmpty()) {
                rm.setReminderApi(api.trim());
            }
        }
        if (commonApis != null) {
            rm.setApis(commonApis);
        }
        return rm;
    }
}
