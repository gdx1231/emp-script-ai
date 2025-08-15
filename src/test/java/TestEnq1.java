import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TestEnq1 {
    public static JSONObject extractTravelDataToJson(String input) {
        JSONObject result = new JSONObject();
        JSONArray daysArray = new JSONArray();
        result.put("enqjnys", daysArray);

        // Parse input as HTML
        Document doc = Jsoup.parse(input);

        Elements gn = doc.select("gn");
        if (!gn.isEmpty()) {
            result.put("gn", gn.get(0).text().trim());
        }
        // Select all <day> elements
        Elements days = doc.select("day");

        for (int i = 0; i < days.size(); i++) {
            Element day = days.get(i);
            JSONObject dayObject = new JSONObject();
            dayObject.put("ENQ_JNY_DAY", i + 1);
            Elements rq = day.select("rq");
            if (!rq.isEmpty()) {
                dayObject.put("ENQ_JNY_DATE", rq.get(0).text().trim());
            }
            // Extract cids
            Elements cids = day.select("cid");
            if (!cids.isEmpty()) {
                dayObject.put("F_ADD", cids.get(0).text());
                dayObject.put("TO_ADD", cids.size() > 1 ? cids.get(1).text() : cids.get(0).text());
                dayObject.put("CITY_ID", cids.size() > 1 ? cids.get(1).text() : cids.get(0).text());
            }

            // Extract enj
            Element enj = day.selectFirst("enj");
            if (enj != null) {
                dayObject.put("ENQ_JNY_CONTENTN", enj.text().trim());
            }

            // Extract service connection IDs
            JSONArray serConns = new JSONArray();
            Elements ids = day.select("id");
            int idIndex = 0;
            for (Element id : ids) {
                if (idIndex >= 3)
                    break; // Limit to 3 IDs as per format
                JSONObject serConn = new JSONObject();
                String idValue = id.text();
                serConn.put("id" + idIndex, idValue.equals("未提供") ? 0 : Integer.parseInt(idValue));
                serConns.put(serConn);
                idIndex++;
            }

            // Pad serConns with zeros if less than 3
            while (serConns.length() < 3) {
                JSONObject serConn = new JSONObject();
                serConn.put("id" + serConns.length(), 0);
                serConns.put(serConn);
            }

            dayObject.put("SER_CONNS", serConns);
            daysArray.put(dayObject);
        }

        return result;
    }

    public static void main(String[] args) {
        String input = """
                    <day>
                ## 第1天：2025年11月21日（周五） 北京 - 悉尼 <cid>291</cid> <cid>292</cid>

                ### 行程摘要：
                <enj>
                - 前往北京首都国际机场T3航站楼集合，搭乘中国国际航空公司CA103航班，从北京飞往悉尼金斯福德·史密斯国际机场，航班时间为10:30-21:50（当地时间），抵达后由专业导游接机，乘车前往酒店办理入住，适应时差，自由活动。
                </enj>

                ### 服务清单：
                - **机票服务：** 国际段直飞航班推荐（北京-悉尼）<id>20056</id>
                - **签证服务：** 澳洲旅游签证 <id>30871</id>
                - **保险服务：** 全程境外旅游保险（救援B计划 保额50万/1-15天）<id>28548</id>
                - **邀请函服务：** 学校或机构邀请函 <id>未提供</id>
                - **接送机服务：** 机场至酒店接送 <id>28484</id>
                - **住宿服务：** Mercure Sydney/双人间 <id>30246</id>
                - **小费服务：** 接送机小费 <id>26084</id>
                </day>
                ---
                <day>
                ## 第2天：2025年11月22日（周六） 悉尼市区深度游学 <cid>292</cid>

                ### 行程摘要：
                <enj>
                - 酒店早餐后，前往悉尼歌剧院，参观建筑外观，了解其历史与文化意义，约1小时；随后前往悉尼大学，参观校园，感受澳洲高等教育氛围，约1小时；午餐后前往邦迪海滩，体验冲浪文化，自由活动约1小时。
                </enj>

                ### 服务清单：
                - **用车：** 25座以上车（全天用车）<id>26066</id>
                - **景点服务：** 悉尼歌剧院中文讲解（成人15+）<id>30095</id>
                - **景点服务：** 悉尼大学参观 <id>未提供</id>
                - **景点服务：** 邦迪海滩自由活动 <id>未提供</id>
                - **午餐：** 午晚餐（六菜一汤）<id>26175</id>
                - **导游服务：** 中文导游讲解（司机加导游）<id>26082</id>
                - **小费：** 全天导游小费 <id>28582</id>
                </day>
                ---
                <day>
                ## 第3天：2025年11月23日（周日） 悉尼 - 黄金海岸 <cid>292</cid> <cid>337</cid>

                ### 行程摘要：
                <enj>
                - 酒店早餐后，乘车前往黄金海岸，途中欣赏沿途风景，抵达后参观天堂农场，与澳洲特有动物互动，体验农场生活，约1.5小时；随后前往华纳电影世界，体验电影主题乐园的魅力，自由活动约3小时。
                </enj>

                ### 服务清单：
                - **用车：** 40-45座车/黄金海岸-布里斯班 <id>28489</id>
                - **景点服务：** 天堂农场 <id>32833</id>
                - **景点服务：** 华纳电影世界 <id>28508</id>
                - **午餐：** 午晚餐（六菜一汤）<id>28498</id>
                - **导游服务：** 中文导游讲解（司机加导游）<id>28499</id>
                - **小费：** 全天导游小费 <id>886</id>
                - **住宿服务：** Dorsett Gold Coast/双人间 <id>30237</id>
                </day>
                ---
                <day>
                ## 第4天：2025年11月24日（周一） 黄金海岸 - 墨尔本 <cid>337</cid> <cid>293</cid>

                ### 行程摘要：
                <enj>
                - 酒店早餐后，乘车前往黄金海岸机场，搭乘航班飞往墨尔本（航班时间待定），抵达后参观墨尔本大学，感受澳洲顶尖学府的学术氛围，约1小时；随后前往疏芬山金矿，体验淘金文化，约1.5小时。
                </enj>

                ### 服务清单：
                - **用车：** 40-45座车/黄金海岸一日游 <id>28488</id>
                - **机票服务：** 黄金海岸-墨尔本航班 <id>未提供</id>
                - **景点服务：** 墨尔本大学参观 <id>未提供</id>
                - **景点服务：** 疏芬山金矿（成人）<id>28435</id>
                - **午餐：** 午晚餐（六菜一汤）<id>28424</id>
                - **导游服务：** 中文导游讲解（司机加导游）<id>30876</id>
                - **小费：** 全天导游小费 <id>30879</id>
                - **住宿服务：** Mercure Melbourne Southbank/双人间 <id>30254</id>
                </day>
                ---
                <day>
                ## 第5天：2025年11月25日（周二） 墨尔本市区游学 - 返程 <cid>293</cid>

                ### 行程摘要：
                <enj>
                - 酒店早餐后，前往菲利浦企鹅岛，观看小企鹅归巢，约1.5小时；随后参观尤利卡观景台，俯瞰墨尔本市景，约1小时；午餐后乘车前往墨尔本机场，搭乘航班返回北京。
                </enj>

                ### 服务清单：
                - **用车：** 25座车（11-20人）/墨尔本（金矿）<id>28597</id>
                - **景点服务：** 菲利浦企鹅岛 <id>38773</id>
                - **景点服务：** 尤利卡观景台（Eureka Skydeck）<id>28978</id>
                - **午餐：** 午晚餐（六菜一汤）<id>28424</id>
                - **导游服务：** 中文导游讲解（司机加导游）<id>30876</id>
                - **小费：** 全天导游小费 <id>30879</id>
                - **机票服务：** 墨尔本-北京航班 <id>9688</id>
                - **接送机服务：** 墨尔本机场接送 <id>28598</id>
                - **小费：** 接送机小费 <id>28586</id>
                </day>
                        """;

        var jsonResult = extractTravelDataToJson(input);
        System.out.println(jsonResult.toString(2)); // Pretty print with indent
    }
}