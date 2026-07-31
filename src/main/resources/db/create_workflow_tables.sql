-- =====================================================================
-- 视频创建工作流 数据库表 DDL (SQL Server 语法)
-- 执行日期: 2026-07-31
--
-- 表清单:
--   AI_WF_DEF       - 工作流定义 (从 workflow.json 加载, MD5 去重)
--   AI_WF_INSTANCE  - 工作流实例 (每次用户提交 = 一条记录)
--   AI_WF_TASK      - 任务 (每个图片/视频/合成 = 一条)
--   AI_WF_ASSET     - 资产 (生成的图片/视频/音频/尾帧)
-- =====================================================================

-- -------------------------------------------------------------------
-- AI_WF_DEF - 工作流定义
-- -------------------------------------------------------------------
CREATE TABLE AI_WF_DEF (
    WFD_ID          BIGINT       IDENTITY(1,1) PRIMARY KEY,
    WFD_NAME        VARCHAR(100)  NOT NULL,           -- 工作流名称 (对应 json.name)
    WFD_DESC        VARCHAR(500),                     -- 描述
    WFD_VERSION     VARCHAR(20),                      -- 版本
    WFD_JSON        NVARCHAR(MAX),                    -- 完整 workflow.json 内容
    WFD_MD5         VARCHAR(32),                      -- JSON 内容 MD5 (去重用)
    WFD_STATUS      VARCHAR(20)   DEFAULT 'USED',     -- USED / DISABLED
    WFD_CDATE       DATETIME      DEFAULT GETDATE(),
    WFD_MDATE       DATETIME      DEFAULT GETDATE()
);

CREATE INDEX IDX_WFD_NAME ON AI_WF_DEF(WFD_NAME, WFD_STATUS);

-- -------------------------------------------------------------------
-- AI_WF_INSTANCE - 工作流实例
-- -------------------------------------------------------------------
CREATE TABLE AI_WF_INSTANCE (
    WFI_ID          BIGINT       IDENTITY(1,1) PRIMARY KEY,
    WFD_ID          BIGINT       NOT NULL,            -- FK -> AI_WF_DEF
    WFI_UID         VARCHAR(100) NOT NULL,            -- 唯一标识 (UUID)
    WFI_STATUS      VARCHAR(20)  DEFAULT 'pending',   -- pending/planning/materials/
                                                      -- generating/compositing/
                                                      -- done/failed/cancelled
    WFI_PRIORITY    INT          DEFAULT 5,           -- 优先级 (1最高, 9最低)
    WFI_INPUT       NVARCHAR(MAX),                    -- 用户输入的故事文本
    WFI_STORYBOARD  NVARCHAR(MAX),                    -- 分镜 JSON (Phase1 输出)
    WFI_RESULT      NVARCHAR(MAX),                    -- 最终结果 JSON (含视频URL)
    WFI_FINAL_URL   VARCHAR(500),                     -- 最终视频 URL
    WFI_ERROR       NVARCHAR(MAX),                    -- 失败原因
    WFI_PROGRESS    INT          DEFAULT 0,           -- 进度 0-100
    WFI_CUR_PHASE   VARCHAR(50),                      -- 当前执行阶段名
    WFI_RETRY_COUNT INT          DEFAULT 0,           -- 工作流级重试次数

    -- 用户归属 (与 AI_CHAT 一致)
    ADM_ID          INT,
    USR_ID          INT,
    SUP_ID          INT,

    WFI_CDATE       DATETIME     DEFAULT GETDATE(),
    WFI_MDATE       DATETIME     DEFAULT GETDATE(),
    WFI_SDATE       DATETIME,                         -- 开始执行时间
    WFI_EDATE       DATETIME,                         -- 完成时间
    WFI_ENGINE_ID   VARCHAR(100)                      -- 执行该实例的引擎进程标识
);

CREATE INDEX IDX_WFI_STATUS ON AI_WF_INSTANCE(WFI_STATUS, WFI_PRIORITY, WFI_CDATE);
CREATE INDEX IDX_WFI_UID    ON AI_WF_INSTANCE(WFI_UID);

-- -------------------------------------------------------------------
-- AI_WF_TASK - 任务
-- -------------------------------------------------------------------
CREATE TABLE AI_WF_TASK (
    WFT_ID            BIGINT       IDENTITY(1,1) PRIMARY KEY,
    WFI_ID            BIGINT       NOT NULL,          -- FK -> AI_WF_INSTANCE
    WFT_TYPE          VARCHAR(50)  NOT NULL,          -- planning / material_img /
                                                      -- shot_video / video_compose / tts
    WFT_NAME          VARCHAR(200),                   -- 任务名称
    WFT_SEQ           INT,                            -- 同类型任务内的序号 (0,1,2...)
    WFT_PHASE         VARCHAR(50),                    -- 所属阶段名

    WFT_STATUS        VARCHAR(20)  DEFAULT 'pending', -- pending/running/succeeded/failed/skipped
    WFT_DEP_ID        BIGINT,                         -- 依赖的前置任务 ID

    WFT_INPUT         NVARCHAR(MAX),                  -- 任务输入 JSON
    WFT_OUTPUT        NVARCHAR(MAX),                  -- 任务输出 JSON

    WFT_PROVIDER      VARCHAR(50),                    -- 供应商标识
    WFT_MODEL         VARCHAR(100),                   -- 模型标识
    WFT_REMOTE_ID     VARCHAR(200),                   -- 远端异步任务 ID
    WFT_REMOTE_STATUS VARCHAR(50),                    -- 远端任务状态

    WFT_ERROR         NVARCHAR(MAX),                  -- 失败原因
    WFT_RETRY_COUNT   INT          DEFAULT 0,         -- 已重试次数

    WFT_CDATE         DATETIME     DEFAULT GETDATE(),
    WFT_MDATE         DATETIME     DEFAULT GETDATE(),
    WFT_SDATE         DATETIME,                       -- 开始执行时间
    WFT_EDATE         DATETIME                        -- 完成时间
);

CREATE INDEX IDX_WFT_INSTANCE ON AI_WF_TASK(WFI_ID, WFT_TYPE, WFT_SEQ);
CREATE INDEX IDX_WFT_STATUS   ON AI_WF_TASK(WFT_STATUS);
CREATE INDEX IDX_WFT_REMOTE   ON AI_WF_TASK(WFT_REMOTE_ID);

-- -------------------------------------------------------------------
-- 注释
-- -------------------------------------------------------------------
EXEC sp_addextendedproperty 'MS_Description', '工作流定义表', 'SCHEMA', 'dbo', 'TABLE', 'AI_WF_DEF';
EXEC sp_addextendedproperty 'MS_Description', '工作流实例表', 'SCHEMA', 'dbo', 'TABLE', 'AI_WF_INSTANCE';
EXEC sp_addextendedproperty 'MS_Description', '工作流任务表', 'SCHEMA', 'dbo', 'TABLE', 'AI_WF_TASK';
