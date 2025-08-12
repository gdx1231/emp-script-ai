import java.util.List;

import com.gdxsoft.ai.modes.*;

public class ModesTest {
    public static void main(String[] args) {
        try {
            String xmlContent = """
<?xml version="1.0" encoding="UTF-8"?>
<modes>
<mode name='chat' description="聊天">
		<step name='init' description="初始化AI的 Prompts">
			<prompts>
				<prompt name='init_prompt' role='system'><![CDATA[
					你是一个智能助手，回答用户的问题。
]]>
					</prompt>
			</prompts>
		</step>
	</mode>
    <mode name='enqjny' description="创建询价单">
        <step name='init' description="初始化AI的 Prompts">
            <prompts>
                <prompt name='init_prompt' role='system'>
                    <![CDATA[
                    你是一位专业的旅游线路规划专家，拥有丰富的全球旅游目的地知识、
                    交通信息、住宿推荐、景点特色、文化习俗及季节性建议。你的任务是根据用户的出行需求，
                    提供个性化、合理、高效且富有体验感的旅游行程规划。
                    ]]>
                </prompt>
                <prompt name='citys' description='城市数据' role='user' sqlRef='citys' dataType='json' />
                <prompt name='ser_conns' description='服务数据' role='user' sqlRef='ser_conns' dataType='json' />
                <prompt name='car_choose' description="人数用车规则" prefix="按人数用车规则如下：" dataType='json'>
                    <![CDATA[
                    [
                        {
                            "min_passengers": 1,
                            "max_passengers": 5,
                            "vehicle_type": "10座以下车"
                        },
                        {
                            "min_passengers": 6,
                            "max_passengers": 12,
                            "vehicle_type": "15座以上车"
                        },
                        {
                            "min_passengers": 13,
                            "max_passengers": 20,
                            "vehicle_type": "25座以上车"
                        },
                        {
                            "min_passengers": 21,
                            "max_passengers": 30,
                            "vehicle_type": "35座以上车"
                        },
                        {
                            "min_passengers": 31,
                            "max_passengers": 40,
                            "vehicle_type": "45座以上车"
                        }
                    ]
                    ]]>
                </prompt>
                <prompt name='enqjny_desc' description='行程描述格式'>
                    <![CDATA[
                    第1天 2025-01-01 周一 北京-墨尔本
                    行程摘要，例如：从北京T3乘坐飞机到墨尔本，航班号: CA1234，出发时间: 2021-09-01 08:00，到达时间: 2021-09-01 10:00
                    服务清单，例如：
                        用车：45坐车-接送机
                        导游：导游服务

                    第2天 2025-01-02 周二  墨尔本
                    行程摘要，例如：抵达机场，到酒店休息，参观天空塔，俯瞰城市风光，大约 1 小时
                    服务清单，例如：
                        用车：45坐车-接送机
                        景点：天空塔
                        午餐：7菜1汤
                        导游：导游服务
                        小费：接送机小费
                    ]]>
                </prompt>
            </prompts>
        </step>
        <step name='export' description="导出行程数据">
            <prompts>
                <prompt name='export_prompt' role='user' description='导出行程数据' dataType='json' prefix="将AI上一次生成的行程数据导出为JSON文件，ENQ_JNY_CONTENT用刚才的“行程摘要”，SER_CONNS为服务清单，请按照以下格式输出：">
                    <![CDATA[
                    {
                        GRP_NAME: "团的名称",
                        GRP_AIR: "出发的航班号",
                        GRP_AIR_WAY: "出发航班出发机场/抵达机场",
                        GRP_AIR_DATE: "出发航班日期时间，例如：2025-01-01 00:30:00",
                        GRP_BACK_AIR: "返航航班号",
                        GRP_BACK_AIR_WAY: "返航航班的出发机场/抵达机场",
                        GRP_BACK_AIR_DATE: "返航航班的日期时间，例如：2025-01-01 12:30:00",
                        enqjnys: [{
                            "TRAFFIC": "交通方式，AIR 或 CAR",
                            "ENQ_JNY_CONTENT": `当天的行程描述，航班描述，游玩景点（景点简介，游览时间）等信息`,
                            "ENQ_JNY_DAY": 1,
                            "ENQ_JNY_DATE": "2025-01-01",
                            "F_ADD": 291,
                            "TO_ADD": 287,
                            "TO_ADD2": 0,
                            "CITY_ID": 287,
                            "SER_CONNS": [{
                                SER_CONN_ID: 35329,
                                SER_KEY_DES: "25座车/接机/送机"
                            }, {
                                SER_CONN_ID: 31525,
                                SER_KEY_DES: "King's College/单人间包含全餐"
                            }]
                        }]
                    }
                    ]]>
                </prompt>
            </prompts>
        </step>
        <sqls>
            <sql name='ser_conns' description='服务数据'>
                <![CDATA[
                SELECT
                    v.SER_CONN_ID,
                    v.SER_KEY_DES,
                    v.CITY_ID,
                    CITY.CITY_NAME,
                    s.SER_NAME,
                    e.hot
                FROM
                    V_SER_CONN_DETAIL v
                    INNER JOIN SER_MAIN s ON v.SER_ID = s.SER_ID
                    AND s.ser_tag IN (@SER_TAG_SPLIT, 'SER_TAG_FEE')
                    INNER JOIN CITY ON v.CITY_ID = CITY.CITY_ID
                    LEFT JOIN SER_KEY k0 ON v.SER_KEY0 = k0.SER_KEY_ID
                    LEFT JOIN (
                        SELECT
                            ser_conn_id,
                            COUNT(*) hot
                        FROM
                            enq_jny_ser_sub
                        GROUP BY
                            ser_conn_id
                    ) e ON v.ser_conn_id = e.ser_conn_id
                WHERE
                    v.CITY_ID IN (@city_ids_split)
                    AND v.PURCHASER_SUP_ID = @g_sup_id
                    AND (
                        (
                            v.ser_id = 1
                            AND v.SER_KEY_NAME0 in (@air_citys_split)
                        )
                        OR v.SER_ID != 1
                    )
                    AND (
                        (
                            v.ser_id = 11
                            AND k0.ref_id = @camp_id
                        )
                        OR v.SER_ID != 11
                    )
                    AND (
                        v.SER_key_des LIKE '%双人间%'
                        OR v.SER_ID != 2
                    )
                    AND (
                        v.SER_key_des NOT LIKE '%三星%'
                        AND v.SER_key_des NOT LIKE '%四星%'
                        OR v.SER_ID != 2
                    )
                    AND v.SER_CONN_STATE = 'USED'
                    AND v.SER_CONN_EDATE > getdate()
                ORDER BY
                    v.SER_ID,
                    hot DESC
                ]]>
            </sql>
            <sql name='citys' description='城市数据'>
                <![CDATA[
                WITH
                    aa AS (
                        SELECT
                            SER_CITY_ID,
                            count(*) AS hot
                        FROM
                            enq_jny_ser_sub a
                            INNER JOIN enq_jny c ON a.enq_jny_id = c.enq_jny_id
                            AND c.ENQ_JNY_DATE > getdate() - 365
                        GROUP BY
                            SER_CITY_ID
                    )
                SELECT
                    b.CITY_ID,
                    b.CITY_NAME,
                    hot
                FROM
                    aa a
                    INNER JOIN CITY b ON a.SER_CITY_ID = b.CITY_ID
                    AND b.COUNTRY_ID != b.CITY_ID
                WHERE
                    b.city_id in (@city_ids_split)
                    AND b.CITY_NAME NOT LIKE '作废%'
                UNION
                SELECT
                    b.CITY_ID,
                    b.CITY_NAME,
                    0
                FROM
                    CITY b
                WHERE
                    CITY_id = @f_city
                ORDER BY
                    hot DESC
                ]]>
            </sql>
        </sqls>
    </mode>
</modes>
""";

            Modes processor = new Modes(xmlContent);
            List<Mode> modes = processor.loadModes();
            Mode mode = modes.get(0);
            // Example usage: Print parsed data
            System.out.println("Mode: " + mode.getName() + ", Description: " + mode.getDescription());
            for (Step step : mode.getSteps()) {
                System.out.println("  Step: " + step.getName() + ", Description: " + step.getDescription());
                for (Prompt prompt : step.getPrompts()) {
                    System.out.println("    Prompt: " + prompt.getName() + ", Role: " + prompt.getRole() + ", Content: "
                            + prompt.getContent());
                }
            }
            for (SqlQuery sql : mode.getSqlQueries()) {
                System.out.println("  SQL: " + sql.getName() + ", Description: " + sql.getDescription());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
