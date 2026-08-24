package com.gdxsoft.ai.voiceclone;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 声音复刻记录持久化（AI_VOICE_CLONE 表）。
 * <p>
 * 每次成功克隆音色后调用 {@link #save} 写入记录；
 * 合成语音时通过 {@link #findByVoiceId} 查找已克隆的音色。
 *
 * @since 1.1.0
 */
public class VoiceCloneDb {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCloneDb.class);

    private final String dbConfigName;

    public VoiceCloneDb(String dbConfigName) {
        this.dbConfigName = dbConfigName;
    }

    /**
     * 保存克隆记录。
     *
     * @param voiceId     音色 ID（克隆 API 返回）
     * @param provider    provider 名称（如 qwen_voice_clone）
     * @param targetModel 绑定的合成模型
     * @param prefix      克隆时的前缀/名称
     * @param audioUrl    源音频 URL
     * @param desc        备注
     * @param admId       管理员 ID（可选）
     * @param usrId       用户 ID（可选）
     * @param supId       供应商 ID（可选）
     * @return 新记录的 AVC_ID
     */
    public long save(String voiceId, String provider, String targetModel,
                     String prefix, String audioUrl, String desc,
                     Integer admId, Integer usrId, Integer supId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("voice_id", voiceId);
        rv.addOrUpdateValue("provider", provider);
        rv.addOrUpdateValue("target_model", targetModel);
        rv.addOrUpdateValue("prefix", prefix);
        rv.addOrUpdateValue("audio_url", audioUrl);
        rv.addOrUpdateValue("desc", desc);
        rv.addOrUpdateValue("status", "USED");
        rv.addOrUpdateValue("adm_id", admId);
        rv.addOrUpdateValue("usr_id", usrId);
        rv.addOrUpdateValue("sup_id", supId);

        String sql = "INSERT INTO AI_VOICE_CLONE (AVC_VOICE_ID, AVC_PROVIDER, AVC_TARGET_MODEL, " +
                "AVC_PREFIX, AVC_AUDIO_URL, AVC_DESC, AVC_STATUS, ADM_ID, USR_ID, SUP_ID, " +
                "AVC_CDATE, AVC_MDATE) " +
                "VALUES (@voice_id, @provider, @target_model, @prefix, @audio_url, @desc, " +
                "@status, @adm_id, @usr_id, @sup_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try {
            long id = DataConnection.insertAndReturnAutoIdLong(sql, "", rv);
            LOGGER.info("声音复刻记录已保存: AVC_ID={}, voice_id={}", id, voiceId);
            return id;
        } catch (Exception e) {
            LOGGER.error("保存声音复刻记录失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存声音复刻记录失败", e);
        }
    }

    /**
     * 按音色 ID 查找记录。
     *
     * @param voiceId 音色 ID
     * @return 记录 JSON，未找到返回 null
     */
    public JSONObject findByVoiceId(String voiceId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("voice_id", voiceId);
        String sql = "SELECT * FROM AI_VOICE_CLONE WHERE AVC_VOICE_ID = @voice_id AND AVC_STATUS = 'USED'";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return rowToJson(tb, 0);
    }

    /**
     * 按 provider 列出所有有效音色。
     *
     * @param provider provider 名称（null 表示全部）
     * @return 记录列表
     */
    public JSONObject listByProvider(String provider) {
        RequestValue rv = new RequestValue();
        String sql;
        if (provider != null && !provider.isEmpty()) {
            rv.addOrUpdateValue("provider", provider);
            sql = "SELECT * FROM AI_VOICE_CLONE WHERE AVC_PROVIDER = @provider AND AVC_STATUS = 'USED' " +
                    "ORDER BY AVC_CDATE DESC";
        } else {
            sql = "SELECT * FROM AI_VOICE_CLONE WHERE AVC_STATUS = 'USED' ORDER BY AVC_CDATE DESC";
        }
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        JSONObject result = new JSONObject();
        result.put("count", tb.getCount());
        org.json.JSONArray arr = new org.json.JSONArray();
        for (int i = 0; i < tb.getCount(); i++) {
            arr.put(rowToJson(tb, i));
        }
        result.put("voices", arr);
        return result;
    }

    /**
     * 按用户列出所有有效音色。
     */
    public JSONObject listByUser(int usrId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("usr_id", usrId);
        String sql = "SELECT * FROM AI_VOICE_CLONE WHERE USR_ID = @usr_id AND AVC_STATUS = 'USED' " +
                "ORDER BY AVC_CDATE DESC";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        JSONObject result = new JSONObject();
        result.put("count", tb.getCount());
        org.json.JSONArray arr = new org.json.JSONArray();
        for (int i = 0; i < tb.getCount(); i++) {
            arr.put(rowToJson(tb, i));
        }
        result.put("voices", arr);
        return result;
    }

    /**
     * 逻辑删除（标记 DELETED）。
     *
     * @param voiceId 音色 ID
     * @return 是否成功更新
     */
    public boolean markDeleted(String voiceId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("voice_id", voiceId);
        String sql = "UPDATE AI_VOICE_CLONE SET AVC_STATUS = 'DELETED', AVC_MDATE = CURRENT_TIMESTAMP " +
                "WHERE AVC_VOICE_ID = @voice_id AND AVC_STATUS = 'USED'";
        DataConnection cnn = new DataConnection(dbConfigName, null);
        cnn.setRequestValue(rv);
        try {
            boolean ok = cnn.executeUpdate(sql);
            if (ok) {
                LOGGER.info("声音复刻记录已标记删除: voice_id={}", voiceId);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("删除声音复刻记录失败: {}", e.getMessage(), e);
            return false;
        } finally {
            cnn.close();
        }
    }

    /**
     * 按目标模型查找绑定的音色（同一模型可能对应多个音色）。
     */
    public JSONObject listByTargetModel(String targetModel) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("target_model", targetModel);
        String sql = "SELECT * FROM AI_VOICE_CLONE WHERE AVC_TARGET_MODEL = @target_model " +
                "AND AVC_STATUS = 'USED' ORDER BY AVC_CDATE DESC";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        JSONObject result = new JSONObject();
        result.put("count", tb.getCount());
        org.json.JSONArray arr = new org.json.JSONArray();
        for (int i = 0; i < tb.getCount(); i++) {
            arr.put(rowToJson(tb, i));
        }
        result.put("voices", arr);
        return result;
    }

    private JSONObject rowToJson(DTTable tb, int rowIndex) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("avc_id", tb.getCell(rowIndex, "AVC_ID").toLong());
            obj.put("voice_id", tb.getCell(rowIndex, "AVC_VOICE_ID").toString());
            obj.put("provider", tb.getCell(rowIndex, "AVC_PROVIDER").toString());
            obj.put("target_model", tb.getCell(rowIndex, "AVC_TARGET_MODEL").toString());
            obj.put("prefix", safeStr(tb, rowIndex, "AVC_PREFIX"));
            obj.put("audio_url", safeStr(tb, rowIndex, "AVC_AUDIO_URL"));
            obj.put("desc", safeStr(tb, rowIndex, "AVC_DESC"));
            obj.put("status", tb.getCell(rowIndex, "AVC_STATUS").toString());
            obj.put("adm_id", tb.getCell(rowIndex, "ADM_ID").toInt());
            obj.put("usr_id", tb.getCell(rowIndex, "USR_ID").toInt());
            obj.put("sup_id", tb.getCell(rowIndex, "SUP_ID").toInt());
            Object cdate = tb.getCell(rowIndex, "AVC_CDATE").getValue();
            if (cdate != null) obj.put("cdate", cdate.toString());
            Object mdate = tb.getCell(rowIndex, "AVC_MDATE").getValue();
            if (mdate != null) obj.put("mdate", mdate.toString());
            return obj;
        } catch (Exception e) {
            LOGGER.error("rowToJson 失败: {}", e.getMessage());
            return new JSONObject();
        }
    }

    private String safeStr(DTTable tb, int row, String col) {
        try {
            Object v = tb.getCell(row, col).getValue();
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // 系统内置音色
    // ================================================================

    /**
     * 初始化系统内置音色到 AI_VOICE_CLONE 表。
     * <p>
     * 已存在的音色（按 voice_id 判断）不会重复插入。
     * 系统音色标记：ADM_ID=USR_ID=SUP_ID=0，AVC_PREFIX='system'。
     *
     * @return 新插入的数量
     */
    public int initSystemVoices() {
        int count = 0;
        for (String[] v : SYSTEM_VOICES) {
            // v: [voiceId, ttsProvider, targetModel, desc]
            if (!existsVoice(v[0])) {
                save(v[0], v[1], v[2], "system", null, v[3], 0, 0, 0);
                count++;
            }
        }
        LOGGER.info("系统音色初始化完成，新增 {} 条（总计 {} 条）", count, SYSTEM_VOICES.length);
        return count;
    }

    /** 检查 voiceId 是否已存在（含系统音色和克隆音色）。 */
    private boolean existsVoice(String voiceId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("voice_id", voiceId);
        String sql = "SELECT COUNT(*) AS CNT FROM AI_VOICE_CLONE WHERE AVC_VOICE_ID = @voice_id";
        try {
            DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
            if (tb.getCount() > 0) {
                return tb.getCell(0, "CNT").toLong() > 0;
            }
        } catch (Exception e) {
            LOGGER.warn("检查音色是否存在失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 系统内置音色定义。
     * <p>
     * 每行：{voiceId, ttsProvider, targetModel, description}
     */
    static final String[][] SYSTEM_VOICES = {
            // ---- Qwen-TTS（qwen3-tts-flash）----
            {"Cherry", "qwen_tts", "qwen3-tts-flash", "Qwen-TTS 默认女声"},

            // ---- Qwen-Audio-TTS（qwen-audio-3.0-tts-flash）----
            {"longanhuan_v3.6", "qwen_tts", "qwen-audio-3.0-tts-flash", "Qwen-Audio-TTS 男声（支持情感标签）"},

            // ---- CosyVoice（cosyvoice-v3-flash）----
            {"longanyang", "qwen_tts", "cosyvoice-v3-flash", "CosyVoice 男声"},
            {"longanhuan_v3", "qwen_tts", "cosyvoice-v3-flash", "CosyVoice 男声（支持指令控制/方言）"},
            {"longshange_v3", "qwen_tts", "cosyvoice-v3-flash", "CosyVoice 男声（支持方言）"},

            // ---- MiniMax（speech-2.8-hd）----
            {"male-qn-qingse", "minimax_tts", "MiniMax/speech-2.8-hd", "MiniMax 男声-青涩"},

            // ---- Doubao（seed-audio-1.0）----
            {"zh_female_cancan_mars_bigtts", "doubao_tts", "seed-audio-1.0", "豆包默认女声-灿灿"},
            {"zh_female_xiaohe_uranus_bigtts", "doubao_tts", "seed-tts-2.0", "豆包女声-小荷（TTS 2.0）"},
    };
}
