/*
 * Copyright (c) 2026 GDX Software
 *
 * 文件名: WorkflowDb.java
 * 描述: 视频创建工作流数据库操作类
 *
 * 表结构:
 *   AI_WF_DEF       - 工作流定义 (workflow.json 缓存)
 *   AI_WF_INSTANCE  - 工作流实例
 *   AI_WF_TASK      - 任务
 *   AI_WF_ASSET     - 资产
 */
package com.gdxsoft.ai.video.workflow.db;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.Utils;

/**
 * 视频创建工作流数据库操作类。
 * <p>
 * 封装 AI_WF_DEF / AI_WF_INSTANCE / AI_WF_TASK / AI_WF_ASSET 四张表的全部操作。
 * 与 {@link com.gdxsoft.ai.ChatManagerDb} 风格一致：所有 SQL 使用 {@code @param}
 * 占位符，通过 {@link RequestValue} 传参，防止 SQL 注入。
 * <p>
 * 典型用法：
 * <pre>{@code
 * // Web 层提交工作流
 * WorkflowDb db = new WorkflowDb(rv, "default");
 * long instanceId = db.submitInstance("text-to-video", storyText, 5);
 *
 * // 引擎层轮询并执行
 * Long id = db.pollPendingInstance("engine-01");
 * db.updateInstanceStatus(id, "planning");
 * long taskId = db.createTask(id, "planning", "分镜拆解", 0, "planning", null);
 * db.updateTaskStatus(taskId, "succeeded", storyboardJson);
 * db.updateInstanceStatus(id, "done");
 * }</pre>
 *
 * @since 1.4.0
 */
public class WorkflowDb {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowDb.class);

    private RequestValue rv;
    private final String dbConfigName;

    /**
     * 工作流实例状态常量。
     */
    public static final String STATUS_PENDING     = "pending";
    public static final String STATUS_PLANNING    = "planning";
    public static final String STATUS_MATERIALS   = "materials";
    public static final String STATUS_GENERATING  = "generating";
    public static final String STATUS_COMPOSITING = "compositing";
    public static final String STATUS_DONE        = "done";
    public static final String STATUS_FAILED      = "failed";
    public static final String STATUS_CANCELLED   = "cancelled";

    /**
     * 任务状态常量。
     */
    public static final String TASK_PENDING   = "pending";
    public static final String TASK_RUNNING   = "running";
    public static final String TASK_SUCCEEDED = "succeeded";
    public static final String TASK_FAILED    = "failed";
    public static final String TASK_SKIPPED   = "skipped";

    /**
     * @param rv           请求参数值对象，贯穿所有 DB 操作的参数上下文
     * @param dbConfigName 数据库配置名称（对应 ewa_conf.xml 中的配置节）
     */
    public WorkflowDb(RequestValue rv, String dbConfigName) {
        this.rv = rv;
        this.dbConfigName = dbConfigName;
    }

    /** 替换参数上下文（用于异步线程中切换 rv） */
    public void setRv(RequestValue rv) {
        if (rv != null) {
            this.rv = rv;
        }
    }

    // =======================================================
    //  AI_WF_DEF - 工作流定义
    // =======================================================

    /**
     * 注册工作流定义（MD5 去重：相同 JSON 内容不重复插入）。
     *
     * @param name        工作流名称
     * @param description 描述
     * @param version     版本
     * @param jsonContent 完整 workflow.json 内容
     * @return WFD_ID（已存在则返回已有记录的 ID）
     */
    public long registerWorkflowDef(String name, String description, String version, String jsonContent) {
        String md5 = Utils.md5(jsonContent);

        // 先查是否已存在同 MD5 的记录
        rv.addOrUpdateValue("_wfd_md5", md5);
        String checkSql = "SELECT WFD_ID FROM AI_WF_DEF WHERE WFD_MD5=@_wfd_md5 AND WFD_STATUS='USED'";
        DTTable tb = DTTable.getJdbcTable(checkSql, dbConfigName, rv);
        if (tb.getCount() > 0) {
            try {
                long existingId = tb.getCell(0, "WFD_ID").toLong();
                LOGGER.info("工作流定义已存在 (MD5={}, WFD_ID={}), 跳过注册", md5, existingId);
                return existingId;
            } catch (Exception e) {
                LOGGER.warn("查询已有工作流定义异常: {}", e.getMessage());
            }
        }

        rv.addOrUpdateValue("_wfd_name", name);
        rv.addOrUpdateValue("_wfd_desc", description != null ? description : "");
        rv.addOrUpdateValue("_wfd_version", version != null ? version : "");
        rv.addOrUpdateValue("_wfd_json", jsonContent);

        String sql = """
                INSERT INTO AI_WF_DEF (WFD_NAME, WFD_DESC, WFD_VERSION, WFD_JSON, WFD_MD5, WFD_STATUS, WFD_CDATE, WFD_MDATE)
                VALUES (@_wfd_name, @_wfd_desc, @_wfd_version, @_wfd_json, @_wfd_md5, 'USED', @sys_date, @sys_date)
                """;
        long id = DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
        LOGGER.info("注册工作流定义: name={}, WFD_ID={}", name, id);
        return id;
    }

    /**
     * 按名称查询工作流定义。
     *
     * @param name 工作流名称
     * @return 定义 JSON（含 WFD_ID, WFD_JSON 等字段），不存在返回 null
     */
    public JSONObject getWorkflowDefByName(String name) {
        rv.addOrUpdateValue("_wfd_name", name);
        String sql = "SELECT * FROM AI_WF_DEF WHERE WFD_NAME=@_wfd_name AND WFD_STATUS='USED' ORDER BY WFD_MDATE DESC";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return tb.getRow(0).toJson("UPPER");
    }

    // =======================================================
    //  AI_WF_INSTANCE - 工作流实例
    // =======================================================

    /**
     * 提交工作流实例（Web 层调用）。
     * <p>
     * 创建一条 pending 状态的实例记录，等待引擎进程轮询拾取。
     *
     * @param wfdId     工作流定义 ID
     * @param uid       唯一标识 (UUID)
     * @param input     用户输入的故事文本
     * @param priority  优先级 (1 最高, 9 最低, 默认 5)
     * @return WFI_ID
     */
    public long submitInstance(long wfdId, String uid, String input, int priority) {
        rv.addOrUpdateValue("_wfd_id", wfdId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_uid", uid, "uuid", 3);
        rv.addOrUpdateValue("_wfi_input", input != null ? input : "");
        rv.addOrUpdateValue("_wfi_priority", priority, "int", 100);

        String sql = """
                INSERT INTO AI_WF_INSTANCE (
                    WFD_ID, WFI_UID, WFI_STATUS, WFI_PRIORITY, WFI_INPUT,
                    WFI_PROGRESS, WFI_RETRY_COUNT, WFI_CDATE, WFI_MDATE,
                    ADM_ID, USR_ID, SUP_ID
                ) VALUES (
                    @_wfd_id, @_wfi_uid, 'pending', @_wfi_priority, @_wfi_input,
                    0, 0, @sys_date, @sys_date,
                    @g_ADM_ID, @G_WEB_USR_ID, @g_SUP_ID
                )
                """;
        long id = DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
        LOGGER.info("提交工作流实例: WFI_ID={}, UID={}, WFD_ID={}", id, uid, wfdId);
        return id;
    }

    /**
     * 轮询 pending 实例并原子占位（引擎调度器调用）。
     * <p>
     * 两步操作：先查出一条 pending 实例，再用乐观锁将其更新为 running。
     * 多引擎并发时只有一个能成功占位。
     *
     * @param engineId 引擎进程标识 (hostname + pid)
     * @return 占位成功的 WFI_ID，无 pending 实例返回 null
     */
    public Long pollPendingInstance(String engineId) {
        // Step 1: 查一条 pending 实例（按优先级、创建时间排序）
        String querySql = """
                SELECT WFI_ID FROM AI_WF_INSTANCE
                WHERE WFI_STATUS='pending'
                ORDER BY WFI_PRIORITY, WFI_CDATE
                """;
        DTTable tb = DTTable.getJdbcTable(querySql, "WFI_ID", 1, 1, "", rv);
        if (tb.getCount() == 0) return null;

        long candidateId;
        try {
            candidateId = tb.getCell(0, "WFI_ID").toLong();
        } catch (Exception e) {
            LOGGER.warn("pending 实例查询异常: {}", e.getMessage());
            return null;
        }

        // Step 2: 乐观锁占位（仅当仍是 pending 时才更新）
        rv.addOrUpdateValue("_wfi_id", candidateId, "bigint", 100);
        rv.addOrUpdateValue("_engine_id", engineId);
        String updateSql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_STATUS='running', WFI_ENGINE_ID=@_engine_id, WFI_SDATE=@sys_date, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id AND WFI_STATUS='pending'
                """;
        DataConnection.updateAndClose(updateSql, dbConfigName, rv);

        // Step 3: 验证是否占位成功
        String checkSql = "SELECT WFI_ENGINE_ID FROM AI_WF_INSTANCE WHERE WFI_ID=@_wfi_id";
        DTTable checkTb = DTTable.getJdbcTable(checkSql, dbConfigName, rv);
        try {
            if (checkTb.getCount() > 0 && engineId.equals(checkTb.getCell(0, "WFI_ENGINE_ID").toString())) {
                LOGGER.info("引擎 {} 占位工作流实例 WFI_ID={}", engineId, candidateId);
                return candidateId;
            }
        } catch (Exception e) {
            LOGGER.warn("占据验证查询异常: {}", e.getMessage());
        }
        // 被其他引擎抢走了
        return null;
    }

    /**
     * 更新实例状态。
     *
     * @param instanceId 实例 ID
     * @param status     新状态（STATUS_* 常量）
     */
    public void updateInstanceStatus(long instanceId, String status) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_status", status);
        String sql = "UPDATE AI_WF_INSTANCE SET WFI_STATUS=@_wfi_status, WFI_MDATE=@sys_date WHERE WFI_ID=@_wfi_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 更新实例当前阶段和进度。
     *
     * @param instanceId 实例 ID
     * @param phase      当前阶段名
     * @param progress   进度 0-100
     */
    public void updateInstanceProgress(long instanceId, String phase, int progress) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_phase", phase);
        rv.addOrUpdateValue("_wfi_progress", progress, "int", 100);
        String sql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_CUR_PHASE=@_wfi_phase, WFI_PROGRESS=@_wfi_progress, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 更新实例成功结果。
     *
     * @param instanceId 实例 ID
     * @param finalUrl   最终视频 URL
     * @param resultJson 完整结果 JSON
     */
    public void updateInstanceResult(long instanceId, String finalUrl, String resultJson) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_final_url", finalUrl != null ? finalUrl : "");
        rv.addOrUpdateValue("_wfi_result", resultJson != null ? resultJson : "");
        String sql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_STATUS='done', WFI_FINAL_URL=@_wfi_final_url, WFI_RESULT=@_wfi_result,
                    WFI_PROGRESS=100, WFI_EDATE=@sys_date, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
        LOGGER.info("工作流实例完成: WFI_ID={}, finalUrl={}", instanceId, finalUrl);
    }

    /**
     * 更新实例失败。
     *
     * @param instanceId 实例 ID
     * @param error      失败原因
     */
    public void updateInstanceError(long instanceId, String error) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_error", error != null ? error : "");
        String sql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_STATUS='failed', WFI_ERROR=@_wfi_error, WFI_EDATE=@sys_date, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
        LOGGER.error("工作流实例失败: WFI_ID={}, error={}", instanceId, error);
    }

    /**
     * 保存分镜 JSON 到实例。
     *
     * @param instanceId  实例 ID
     * @param storyboardJson 分镜 JSON 字符串
     */
    public void saveStoryboard(long instanceId, String storyboardJson) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wfi_storyboard", storyboardJson != null ? storyboardJson : "");
        String sql = "UPDATE AI_WF_INSTANCE SET WFI_STORYBOARD=@_wfi_storyboard, WFI_MDATE=@sys_date WHERE WFI_ID=@_wfi_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 按 UID 查询实例（Web 层状态轮询用）。
     *
     * @param uid 工作流实例 UUID
     * @return 实例 JSON（大写键名），不存在返回 null
     */
    public JSONObject queryInstanceByUid(String uid) {
        rv.addOrUpdateValue("_wfi_uid", uid, "uuid", 36);
        String sql = "SELECT * FROM AI_WF_INSTANCE WHERE WFI_UID=@_wfi_uid";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return tb.getRow(0).toJson("UPPER");
    }

    /**
     * 按 ID 查询实例。
     *
     * @param instanceId 实例 ID
     * @return 实例 JSON（大写键名），不存在返回 null
     */
    public JSONObject queryInstanceById(long instanceId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        String sql = "SELECT * FROM AI_WF_INSTANCE WHERE WFI_ID=@_wfi_id";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return tb.getRow(0).toJson("UPPER");
    }

    /**
     * 查询需要恢复的实例（引擎重启时调用）。
     * <p>
     * 查找由指定引擎占位但状态非终态的实例。
     *
     * @param engineId 引擎标识
     * @return 实例 JSON 列表
     */
    public List<JSONObject> queryRecoverableInstances(String engineId) {
        rv.addOrUpdateValue("_engine_id", engineId);
        String sql = """
                SELECT WFI_ID, WFI_STATUS, WFI_CUR_PHASE FROM AI_WF_INSTANCE
                WHERE WFI_ENGINE_ID=@_engine_id
                AND WFI_STATUS IN ('planning', 'materials', 'generating', 'compositing')
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /**
     * 重置实例为 pending（恢复时调用，从头重跑当前阶段）。
     *
     * @param instanceId 实例 ID
     */
    public void resetInstanceToPending(long instanceId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        String sql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_STATUS='pending', WFI_ENGINE_ID=NULL, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 取消实例。
     *
     * @param instanceId 实例 ID
     */
    public void cancelInstance(long instanceId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        String sql = """
                UPDATE AI_WF_INSTANCE
                SET WFI_STATUS='cancelled', WFI_EDATE=@sys_date, WFI_MDATE=@sys_date
                WHERE WFI_ID=@_wfi_id AND WFI_STATUS NOT IN ('done', 'failed', 'cancelled')
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    // =======================================================
    //  AI_WF_TASK - 任务
    // =======================================================

    /**
     * 创建任务。
     *
     * @param instanceId 实例 ID
     * @param type       任务类型 (planning/material_img/shot_video/video_compose/tts)
     * @param name       任务名称
     * @param seq        序号
     * @param phase      所属阶段名
     * @param inputJson  任务输入 JSON（可为 null）
     * @return WFT_ID
     */
    public long createTask(long instanceId, String type, String name, int seq, String phase, String inputJson) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wft_type", type);
        rv.addOrUpdateValue("_wft_name", name != null ? name : "");
        rv.addOrUpdateValue("_wft_seq", seq, "int", 100);
        rv.addOrUpdateValue("_wft_phase", phase != null ? phase : "");
        rv.addOrUpdateValue("_wft_input", inputJson != null ? inputJson : "");

        String sql = """
                INSERT INTO AI_WF_TASK (WFI_ID, WFT_TYPE, WFT_NAME, WFT_SEQ, WFT_PHASE,
                    WFT_STATUS, WFT_INPUT, WFT_RETRY_COUNT, WFT_CDATE, WFT_MDATE)
                VALUES (@_wfi_id, @_wft_type, @_wft_name, @_wft_seq, @_wft_phase,
                    'pending', @_wft_input, 0, @sys_date, @sys_date)
                """;
        return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
    }

    /**
     * 创建有依赖的任务（尾帧续接：shot N 依赖 shot N-1）。
     *
     * @param instanceId 实例 ID
     * @param type       任务类型
     * @param name       任务名称
     * @param seq        序号
     * @param phase      所属阶段
     * @param inputJson  输入 JSON
     * @param depTaskId  依赖的前置任务 ID
     * @return WFT_ID
     */
    public long createTaskWithDep(long instanceId, String type, String name, int seq,
                                  String phase, String inputJson, long depTaskId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wft_type", type);
        rv.addOrUpdateValue("_wft_name", name != null ? name : "");
        rv.addOrUpdateValue("_wft_seq", seq, "int", 100);
        rv.addOrUpdateValue("_wft_phase", phase != null ? phase : "");
        rv.addOrUpdateValue("_wft_input", inputJson != null ? inputJson : "");
        rv.addOrUpdateValue("_wft_dep_id", depTaskId, "bigint", 100);

        String sql = """
                INSERT INTO AI_WF_TASK (WFI_ID, WFT_TYPE, WFT_NAME, WFT_SEQ, WFT_PHASE,
                    WFT_STATUS, WFT_INPUT, WFT_DEP_ID, WFT_RETRY_COUNT, WFT_CDATE, WFT_MDATE)
                VALUES (@_wfi_id, @_wft_type, @_wft_name, @_wft_seq, @_wft_phase,
                    'pending', @_wft_input, @_wft_dep_id, 0, @sys_date, @sys_date)
                """;
        return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
    }

    /**
     * 更新任务状态和输出。
     *
     * @param taskId     任务 ID
     * @param status     新状态（TASK_* 常量）
     * @param outputJson 输出 JSON（可为 null）
     */
    public void updateTaskStatus(long taskId, String status, String outputJson) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        rv.addOrUpdateValue("_wft_status", status);
        rv.addOrUpdateValue("_wft_output", outputJson != null ? outputJson : "");

        String endTime = TASK_SUCCEEDED.equals(status) || TASK_FAILED.equals(status)
                ? ", WFT_EDATE=@sys_date" : "";
        String sql = "UPDATE AI_WF_TASK SET WFT_STATUS=@_wft_status, WFT_OUTPUT=@_wft_output" +
                endTime + ", WFT_MDATE=@sys_date WHERE WFT_ID=@_wft_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 标记任务开始执行。
     *
     * @param taskId 任务 ID
     */
    public void markTaskRunning(long taskId) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        String sql = "UPDATE AI_WF_TASK SET WFT_STATUS='running', WFT_SDATE=@sys_date, WFT_MDATE=@sys_date WHERE WFT_ID=@_wft_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 更新任务的供应商和远端任务 ID（视频生成异步提交后调用）。
     *
     * @param taskId    任务 ID
     * @param provider  供应商标识
     * @param model     模型标识
     * @param remoteId  远端异步任务 ID
     */
    public void updateTaskRemoteId(long taskId, String provider, String model, String remoteId) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        rv.addOrUpdateValue("_wft_provider", provider != null ? provider : "");
        rv.addOrUpdateValue("_wft_model", model != null ? model : "");
        rv.addOrUpdateValue("_wft_remote_id", remoteId != null ? remoteId : "");

        String sql = """
                UPDATE AI_WF_TASK
                SET WFT_PROVIDER=@_wft_provider, WFT_MODEL=@_wft_model,
                    WFT_REMOTE_ID=@_wft_remote_id, WFT_MDATE=@sys_date
                WHERE WFT_ID=@_wft_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 更新远端任务状态（轮询时调用）。
     *
     * @param taskId       任务 ID
     * @param remoteStatus 远端状态 (processing/succeeded/failed)
     */
    public void updateTaskRemoteStatus(long taskId, String remoteStatus) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        rv.addOrUpdateValue("_wft_remote_status", remoteStatus != null ? remoteStatus : "");
        String sql = "UPDATE AI_WF_TASK SET WFT_REMOTE_STATUS=@_wft_remote_status, WFT_MDATE=@sys_date WHERE WFT_ID=@_wft_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 记录任务失败。
     *
     * @param taskId 任务 ID
     * @param error  失败原因
     */
    public void updateTaskError(long taskId, String error) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        rv.addOrUpdateValue("_wft_error", error != null ? error : "");
        String sql = """
                UPDATE AI_WF_TASK
                SET WFT_STATUS='failed', WFT_ERROR=@_wft_error, WFT_EDATE=@sys_date, WFT_MDATE=@sys_date
                WHERE WFT_ID=@_wft_id
                """;
        DataConnection.updateAndClose(sql, dbConfigName, rv);
    }

    /**
     * 增加任务重试计数。
     *
     * @param taskId 任务 ID
     * @return 更新后的重试次数
     */
    public int incrementTaskRetry(long taskId) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        // 先查当前重试次数
        String querySql = "SELECT coalesce(WFT_RETRY_COUNT, 0) AS RETRY FROM AI_WF_TASK WHERE WFT_ID=@_wft_id";
        DTTable tb = DTTable.getJdbcTable(querySql, dbConfigName, rv);
        if (tb.getCount() == 0) return 0;
        int currentRetry;
        try {
            currentRetry = tb.getCell(0, "RETRY").toLong().intValue();
        } catch (Exception e) {
            LOGGER.warn("查询重试次数异常: {}", e.getMessage());
            currentRetry = 0;
        }
        int newRetry = currentRetry + 1;

        rv.addOrUpdateValue("_wft_retry", newRetry, "int", 100);
        String sql = "UPDATE AI_WF_TASK SET WFT_RETRY_COUNT=@_wft_retry, WFT_STATUS='pending', WFT_MDATE=@sys_date WHERE WFT_ID=@_wft_id";
        DataConnection.updateAndClose(sql, dbConfigName, rv);
        return newRetry;
    }

    /**
     * 查询实例下的所有任务。
     *
     * @param instanceId 实例 ID
     * @return 任务 JSON 列表（按 WFT_SEQ 排序）
     */
    public List<JSONObject> queryTasks(long instanceId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        String sql = """
                SELECT * FROM AI_WF_TASK
                WHERE WFI_ID=@_wfi_id
                ORDER BY WFT_SEQ
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /**
     * 查询实例下指定类型的任务。
     *
     * @param instanceId 实例 ID
     * @param type       任务类型
     * @return 任务 JSON 列表
     */
    public List<JSONObject> queryTasksByType(long instanceId, String type) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_wft_type", type);
        String sql = """
                SELECT * FROM AI_WF_TASK
                WHERE WFI_ID=@_wfi_id AND WFT_TYPE=@_wft_type
                ORDER BY WFT_SEQ
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /**
     * 查询需要恢复的远端任务（引擎重启时调用）。
     * <p>
     * 查找有远端任务 ID 且远端状态为 processing 的任务。
     *
     * @param instanceId 实例 ID
     * @return 任务 JSON 列表
     */
    public List<JSONObject> queryRecoverableRemoteTasks(long instanceId) {
        rv.addOrUpdateValue("_wfi_id", instanceId, "bigint", 100);
        String sql = """
                SELECT * FROM AI_WF_TASK
                WHERE WFI_ID=@_wfi_id
                AND WFT_REMOTE_ID IS NOT NULL AND WFT_REMOTE_ID<>''
                AND WFT_REMOTE_STATUS='processing'
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /**
     * 按 ID 查询单个任务。
     *
     * @param taskId 任务 ID
     * @return 任务 JSON，不存在返回 null
     */
    public JSONObject queryTaskById(long taskId) {
        rv.addOrUpdateValue("_wft_id", taskId, "bigint", 100);
        String sql = "SELECT * FROM AI_WF_TASK WHERE WFT_ID=@_wft_id";
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return tb.getRow(0).toJson("UPPER");
    }

    // =======================================================
    //  资产 (复用 AI_CHAT_EXP_ATTS 表)
    //
    //  字段映射:
    //    FILE_FROM      = 资产类型 (char_img/env_img/shot_video/last_frame/final_video/tts_audio)
    //    FILE_RID       = WFI_ID (工作流实例)
    //    FILE_RID1      = WFT_ID (任务, 可为 0)
    //    FILE_PARA0     = refType  (character/environment/shot)
    //    FILE_PARA1     = refName  (如 "小明" / "教室" / "shot_2")
    //    FILE_NAME      = 资产名称
    //    FILE_PATH      = URL
    //    FILE_REAL_PATH = 本地路径
    //    FILE_DES       = 元数据 JSON
    //    FILE_SIZE      = 文件大小 (字节)
    //    FILE_STATUS    = 'USED'
    //    FILE_UNID      = UUID
    // =======================================================

    /**
     * 保存资产（写入 AI_CHAT_EXP_ATTS）。
     */
    public long saveAsset(long instanceId, long taskId, String type, String refType,
                          String refName, String name, String url, String localPath, String metadata) {
        rv.addOrUpdateValue("_file_rid", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_file_rid1", taskId, "bigint", 100);
        rv.addOrUpdateValue("_file_from", type);
        rv.addOrUpdateValue("_file_para0", refType != null ? refType : "");
        rv.addOrUpdateValue("_file_para1", refName != null ? refName : "");
        rv.addOrUpdateValue("_file_name", name != null ? name : "");
        rv.addOrUpdateValue("_file_path", url != null ? url : "");
        rv.addOrUpdateValue("_file_real_path", localPath != null ? localPath : "");
        rv.addOrUpdateValue("_file_des", metadata != null ? metadata : "");
        rv.addOrUpdateValue("_file_unid", java.util.UUID.randomUUID().toString(), "uuid", 36);

        String sql = """
                INSERT INTO AI_CHAT_EXP_ATTS (
                    FILE_RID, FILE_RID1, FILE_FROM, FILE_PARA0, FILE_PARA1,
                    FILE_NAME, FILE_PATH, FILE_REAL_PATH, FILE_DES,
                    FILE_STATUS, FILE_UNID, FILE_CDATE,
                    SUP_ID, ADM_ID
                ) VALUES (
                    @_file_rid, @_file_rid1, @_file_from, @_file_para0, @_file_para1,
                    @_file_name, @_file_path, @_file_real_path, @_file_des,
                    'USED', @_file_unid, @sys_date,
                    @g_SUP_ID, @g_ADM_ID
                )
                """;
        return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
    }

    /**
     * 查询实例下的所有资产（带别名映射保持兼容）。
     */
    public List<JSONObject> queryAssets(long instanceId) {
        rv.addOrUpdateValue("_file_rid", instanceId, "bigint", 100);
        String sql = """
                SELECT FILE_ID    AS WFA_ID,
                       FILE_FROM  AS WFA_TYPE,
                       FILE_PARA0 AS WFA_REF_TYPE,
                       FILE_PARA1 AS WFA_REF_NAME,
                       FILE_NAME  AS WFA_NAME,
                       FILE_PATH  AS WFA_URL,
                       FILE_REAL_PATH AS WFA_LOCAL_PATH,
                       FILE_DES   AS WFA_METADATA,
                       FILE_SIZE  AS WFA_SIZE,
                       FILE_CDATE AS WFA_CDATE
                FROM AI_CHAT_EXP_ATTS
                WHERE FILE_RID=@_file_rid AND FILE_STATUS='USED'
                ORDER BY FILE_ID
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /** 查询指定类型的资产 */
    public List<JSONObject> queryAssetsByType(long instanceId, String type) {
        rv.addOrUpdateValue("_file_rid", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_file_from", type);
        String sql = """
                SELECT FILE_ID    AS WFA_ID,
                       FILE_FROM  AS WFA_TYPE,
                       FILE_PARA0 AS WFA_REF_TYPE,
                       FILE_PARA1 AS WFA_REF_NAME,
                       FILE_NAME  AS WFA_NAME,
                       FILE_PATH  AS WFA_URL,
                       FILE_REAL_PATH AS WFA_LOCAL_PATH,
                       FILE_DES   AS WFA_METADATA,
                       FILE_SIZE  AS WFA_SIZE,
                       FILE_CDATE AS WFA_CDATE
                FROM AI_CHAT_EXP_ATTS
                WHERE FILE_RID=@_file_rid AND FILE_FROM=@_file_from AND FILE_STATUS='USED'
                ORDER BY FILE_ID
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tb.getCount(); i++) {
            list.add(tb.getRow(i).toJson("UPPER"));
        }
        return list;
    }

    /** 按引用名查询最新资产 */
    public JSONObject queryAssetByRef(long instanceId, String refType, String refName) {
        rv.addOrUpdateValue("_file_rid", instanceId, "bigint", 100);
        rv.addOrUpdateValue("_file_para0", refType);
        rv.addOrUpdateValue("_file_para1", refName);
        String sql = """
                SELECT TOP 1
                       FILE_ID    AS WFA_ID,
                       FILE_FROM  AS WFA_TYPE,
                       FILE_PARA0 AS WFA_REF_TYPE,
                       FILE_PARA1 AS WFA_REF_NAME,
                       FILE_NAME  AS WFA_NAME,
                       FILE_PATH  AS WFA_URL,
                       FILE_REAL_PATH AS WFA_LOCAL_PATH,
                       FILE_DES   AS WFA_METADATA
                FROM AI_CHAT_EXP_ATTS
                WHERE FILE_RID=@_file_rid
                  AND FILE_PARA0=@_file_para0
                  AND FILE_PARA1=@_file_para1
                  AND FILE_STATUS='USED'
                ORDER BY FILE_ID DESC
                """;
        DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
        if (tb.getCount() == 0) return null;
        return tb.getRow(0).toJson("UPPER");
    }

    // =======================================================
    //  辅助方法
    // =======================================================

     
}
