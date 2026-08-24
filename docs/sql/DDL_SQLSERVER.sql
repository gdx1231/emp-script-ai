-- =====================================================================
-- 视频创作工作流 DDL — SQL Server (OneWorld 库规范)
-- 全部 11 张表, 完整建库模版
-- =====================================================================

-- ==========================================================
-- 1. AI_WF_DEF — 工作流定义 (workflow.json 缓存, MD5 去重)
-- ==========================================================
CREATE TABLE AI_WF_DEF (
    WFD_ID      BIGINT IDENTITY(1,1) NOT NULL,              -- 主键
    WFD_NAME    NVARCHAR(100) NOT NULL, -- 工作流名称
    WFD_DESC    NVARCHAR(500) NULL,     -- 描述
    WFD_VERSION VARCHAR(20)  NULL,      -- 版本
    WFD_JSON    NVARCHAR(MAX) NULL,     -- 完整 workflow.json
    WFD_MD5     VARCHAR(32)  NULL,      -- JSON MD5 (去重)
    WFD_STATUS  VARCHAR(20)  DEFAULT 'USED', -- USED/DISABLED
    WFD_CDATE   DATETIME DEFAULT GETDATE(),                        -- 创建时间
    WFD_MDATE   DATETIME DEFAULT GETDATE(),                        -- 修改时间
    CONSTRAINT PK_AI_WF_DEF PRIMARY KEY (WFD_ID)
);
CREATE NONCLUSTERED INDEX IDX_WFD_NAME ON AI_WF_DEF(WFD_NAME, WFD_STATUS);

-- ==========================================================
-- 2. AI_WF_INSTANCE — 工作流实例 (每次用户提交=一条记录)
-- ==========================================================
CREATE TABLE AI_WF_INSTANCE (
    WFI_ID          BIGINT IDENTITY(1,1) NOT NULL,                -- 主键
    WFD_ID          BIGINT NOT NULL,                              -- FK→AI_WF_DEF
    WFI_UID         UNIQUEIDENTIFIER NOT NULL,                     -- 唯一标识(UUID)
    WFI_STATUS      VARCHAR(20)  DEFAULT 'pending', -- pending/planning/materials/generating/compositing/done/failed/cancelled
    WFI_PRIORITY    INT DEFAULT 5,                                 -- 优先级 1最高 9最低
    WFI_INPUT       NVARCHAR(MAX) NULL,  -- 用户输入(故事文本)
    WFI_STORYBOARD  NVARCHAR(MAX) NULL,  -- 分镜JSON(Phase1输出)
    WFI_RESULT      NVARCHAR(MAX) NULL,  -- 最终结果JSON
    WFI_FINAL_URL   VARCHAR(500) NULL,   -- 最终视频URL
    WFI_ERROR       NVARCHAR(MAX) NULL,  -- 失败原因
    WFI_PROGRESS    INT DEFAULT 0,                                  -- 进度 0-100
    WFI_CUR_PHASE   VARCHAR(50)  NULL,   -- 当前执行阶段名
    WFI_RETRY_COUNT INT DEFAULT 0,                                  -- 工作流级重试次数
    ADM_ID          INT NULL,                                       -- 管理员
    USR_ID          INT NULL,                                       -- 用户
    SUP_ID          INT NULL,                                       -- 供应商
    WFI_CDATE       DATETIME DEFAULT GETDATE(),                     -- 创建时间
    WFI_MDATE       DATETIME DEFAULT GETDATE(),                     -- 修改时间
    WFI_SDATE       DATETIME NULL,                                  -- 开始执行时间
    WFI_EDATE       DATETIME NULL,                                  -- 完成时间
    WFI_ENGINE_ID   VARCHAR(100) NULL,   -- 执行引擎标识(hostname+pid)
    CONSTRAINT PK_AI_WF_INSTANCE PRIMARY KEY (WFI_ID)
);
CREATE NONCLUSTERED INDEX IDX_WFI_STATUS ON AI_WF_INSTANCE(WFI_STATUS, WFI_PRIORITY, WFI_CDATE);
CREATE NONCLUSTERED INDEX IDX_WFI_UID    ON AI_WF_INSTANCE(WFI_UID);

-- ==========================================================
-- 3. AI_WF_TASK — 任务 (每张图/每个分镜视频/合成=一条)
-- ==========================================================
CREATE TABLE AI_WF_TASK (
    WFT_ID            BIGINT IDENTITY(1,1) NOT NULL,               -- 主键
    WFI_ID            BIGINT NOT NULL,                              -- FK→AI_WF_INSTANCE
    WFT_TYPE          VARCHAR(50)  NOT NULL, -- planning/material_img/shot_video/video_compose/tts
    WFT_NAME          NVARCHAR(200) NULL, -- 任务名称(如"人物图:小明"/"分镜3视频")
    WFT_SEQ           INT NULL,                                     -- 同类型任务序号(0,1,2...)
    WFT_PHASE         VARCHAR(50)  NULL,  -- 所属阶段名
    WFT_STATUS        VARCHAR(20)  DEFAULT 'pending', -- pending/running/succeeded/failed/skipped
    WFT_DEP_ID        BIGINT NULL,                                  -- 依赖前置任务ID(尾帧续接)
    WFT_INPUT         NVARCHAR(MAX) NULL, -- 任务输入JSON
    WFT_OUTPUT        NVARCHAR(MAX) NULL, -- 任务输出JSON
    WFT_PROVIDER      VARCHAR(50)  NULL,  -- 供应商标识
    WFT_MODEL         VARCHAR(100) NULL,  -- 模型标识
    WFT_REMOTE_ID     VARCHAR(200) NULL,  -- 远端异步任务ID
    WFT_REMOTE_STATUS VARCHAR(50)  NULL,  -- 远端任务状态(processing/succeeded/failed)
    WFT_ERROR         NVARCHAR(MAX) NULL, -- 失败原因
    WFT_RETRY_COUNT   INT DEFAULT 0,                                 -- 已重试次数
    WFT_CDATE         DATETIME DEFAULT GETDATE(),                    -- 创建时间
    WFT_MDATE         DATETIME DEFAULT GETDATE(),                    -- 修改时间
    WFT_SDATE         DATETIME NULL,                                 -- 开始执行时间
    WFT_EDATE         DATETIME NULL,                                 -- 完成时间
    CONSTRAINT PK_AI_WF_TASK PRIMARY KEY (WFT_ID)
);
CREATE NONCLUSTERED INDEX IDX_WFT_INSTANCE ON AI_WF_TASK(WFI_ID, WFT_TYPE, WFT_SEQ);
CREATE NONCLUSTERED INDEX IDX_WFT_STATUS   ON AI_WF_TASK(WFT_STATUS);
CREATE NONCLUSTERED INDEX IDX_WFT_REMOTE   ON AI_WF_TASK(WFT_REMOTE_ID);

-- ==========================================================
-- 4. AI_CHAT — 聊天会话
-- ==========================================================
CREATE TABLE AI_CHAT (
    AI_ID        BIGINT IDENTITY(1,1) NOT NULL,                     -- 主键
    AI_UID       UNIQUEIDENTIFIER NOT NULL,                          -- 请求编号
    AI_PROVIDER  VARCHAR(20)  NOT NULL,   -- AI供应商
    AI_MODEL     VARCHAR(50)  NOT NULL,   -- 模型
    AI_THINKING  BIT NULL,                                           -- 思考模式
    AI_STREAM    BIT NULL,                                           -- 流模式
    AI_MODE      VARCHAR(20)  NOT NULL,   -- 交互模式
    AI_MAX_TOKEN INT NULL,                                           -- 最大Token限制
    AI_CDATE     DATETIME NOT NULL,                                  -- 创建时间
    AI_MDATE     DATETIME NOT NULL,                                  -- 修改时间
    ADM_ID       INT NULL,                                           -- 管理员
    USR_ID       INT NULL,                                           -- 用户
    SUP_ID       INT NULL,                                           -- 供应商
    AI_CUR_STEP  VARCHAR(50)  NOT NULL,   -- 当前步骤
    AI_PID       BIGINT NULL,                                        -- 父会话ID(子mode场景)
    AI_REF       VARCHAR(50)  NULL,       -- 关联类型('video_workflow')
    AI_REF_ID    VARCHAR(50)  NULL,       -- 关联ID(WFI_UID)
    CONSTRAINT PK_AI_CHAT PRIMARY KEY (AI_ID)
);
CREATE NONCLUSTERED INDEX IDX_AI_CHAT_REF ON AI_CHAT(AI_REF, AI_REF_ID);

-- ==========================================================
-- 5. AI_CHAT_MSG — 聊天消息
-- ==========================================================
CREATE TABLE AI_CHAT_MSG (
    AIM_ID               BIGINT IDENTITY(1,1) NOT NULL,            -- 主键
    AI_ID                BIGINT NOT NULL,                           -- FK→AI_CHAT
    AIM_NOI              SMALLINT DEFAULT 0 NOT NULL,              -- 回话轮次
    AIM_ROLE             VARCHAR(20) NOT NULL, -- system/user/assistant/agent
    AIM_BY_USER          BIT DEFAULT 0 NULL,                        -- 用户提问
    AIM_SKIP_APPEND      BIT NULL,                                  -- 下次回话不附加
    AIM_MSG              NVARCHAR(MAX) NOT NULL, -- 消息内容
    AIM_STEP             VARCHAR(50)  NULL, -- 当前步骤
    AIM_ACTION           VARCHAR(50)  NULL, -- 执行动作
    AIM_ACTION_CLASS     VARCHAR(200) NULL, -- 执行类
    AIM_PROMPT_NAME      VARCHAR(50)  NULL, -- 提示词名称
    AIM_TOTAL_TOKENS     INT NULL,                                  -- 总词元
    AIM_COMPLETION_TOKENS INT NULL,                                 -- 输出词元
    AIM_PROMPT_TOKENS    INT NULL,                                  -- 输入词元
    AIM_CACHED_TOKENS    INT NULL,                                  -- 缓存词元
    AIM_TIME_BEGIN       DATETIME NOT NULL,                         -- 开始时间
    AIM_TIME_END         DATETIME NULL,                             -- 结束时间
    CONSTRAINT PK_AI_CHAT_MSG PRIMARY KEY (AIM_ID)
);

-- ==========================================================
-- 6. AI_CHAT_EXP_ATTS — 文件资产 (WorkflowDb.saveAsset())
-- ==========================================================
CREATE TABLE AI_CHAT_EXP_ATTS (
    FILE_ID        BIGINT IDENTITY(1,1) NOT NULL,                   -- 主键
    FILE_NAME      NVARCHAR(150) NULL,   -- 资产名称
    FILE_DES       NVARCHAR(500) NULL,   -- 元数据JSON
    FILE_UNID      CHAR(36) NULL,        -- UUID
    FILE_EXT       VARCHAR(20)  NULL,    -- 扩展名
    FILE_FROM      VARCHAR(100) NULL,    -- 资产类型(char_img/env_img/shot_video/last_frame/final_video/tts_audio)
    FILE_RID       BIGINT NULL,                                     -- WFI_ID(工作流实例)
    FILE_RID1      BIGINT NULL,                                     -- WFT_ID(任务)
    FILE_RID2      BIGINT NULL,                                     -- 扩展引用ID2
    FILE_PARA0     VARCHAR(50)  NULL,    -- refType(character/environment/shot)
    FILE_PARA1     VARCHAR(50)  NULL,    -- refName(角色名/场景名/"shot_N")
    FILE_PARA2     VARCHAR(50)  NULL,    -- 扩展参数2
    FILE_KEYWORD   NVARCHAR(150) NULL,   -- 关键词
    FILE_STATUS    VARCHAR(50)  DEFAULT 'USED', -- 状态
    FILE_SIZE      INT NULL,                                        -- 文件大小(字节)
    FILE_PATH      VARCHAR(300) NULL,    -- 远端URL
    FILE_REAL_PATH VARCHAR(500) NULL,    -- 本地文件路径
    FILE_ORD       INT NULL,                                        -- 排序
    FILE_MD5       VARCHAR(32)  NULL,    -- MD5
    FILE_UP_UA     VARCHAR(MAX) NULL,    -- 上传UA
    FILE_UP_IP     VARCHAR(40)  NULL,    -- 上传IP
    FILE_UP_JSP    VARCHAR(MAX) NULL,    -- 上传JSP
    SUP_ID         INT NULL,                                        -- 供应商
    ADM_ID         INT NULL,                                        -- 管理员
    FILE_MDATE     DATETIME NULL,                                   -- 修改时间
    FILE_CDATE     DATETIME NULL,                                   -- 创建时间
    CONSTRAINT PK_AI_CHAT_EXP_ATTS PRIMARY KEY (FILE_ID)
);

-- ==========================================================
-- 7. AI_CHAT_PARAMS — 聊天参数
-- ==========================================================
CREATE TABLE AI_CHAT_PARAMS (
    AIP_ID   BIGINT IDENTITY(1,1) NOT NULL,                         -- 主键
    AI_ID    BIGINT NOT NULL,                                        -- FK→AI_CHAT
    AIM_ID   BIGINT NOT NULL,                                        -- FK→AI_CHAT_MSG
    AIP_NAME NVARCHAR(100) NOT NULL,     -- 参数名
    AIP_VAL  NVARCHAR(MAX) NULL,         -- 参数值
    AIP_TYPE NVARCHAR(50)  NULL,         -- 参数类型
    CONSTRAINT PK_AI_CHAT_PARAMS PRIMARY KEY (AIP_ID)
);

-- ==========================================================
-- 8. AI_CREATE_VIDEO_LIB — 视频库 (CompositingPhase 输出)
-- ==========================================================
CREATE TABLE AI_CREATE_VIDEO_LIB (
    ACVL_ID       BIGINT NOT NULL,                                   -- 主键
    ACVL_NAME     NVARCHAR(100) NOT NULL, -- 视频名称
    ACVL_DES      NVARCHAR(MAX) NULL,     -- 描述
    ACVL_UNID     UNIQUEIDENTIFIER NOT NULL,                          -- UUID
    ACVL_TYPE     VARCHAR(20)  NULL,      -- 类型(final_composite等)
    ACVL_EXT      VARCHAR(20)  NULL,      -- 扩展名(mp4)
    ACVL_SIZE     INT NULL,                                          -- 文件大小(字节)
    ACVL_MD5      VARCHAR(32)  NULL,      -- MD5
    ACVL_PATH     VARCHAR(300) NULL,      -- URL/相对路径
    ACVL_REALPATH VARCHAR(500) NULL,      -- 本地绝对路径
    ACVL_UP_UA    VARCHAR(MAX) NULL,      -- 上传UA
    ACVL_UP_IP    VARCHAR(40)  NULL,      -- 上传IP
    ACVL_UP_JSP   VARCHAR(MAX) NULL,      -- 上传JSP
    ACVL_CDATE    DATETIME NULL,                                     -- 创建时间
    ACVL_MDATE    DATETIME NULL,                                     -- 修改时间
    ACVL_STATUS   VARCHAR(4) DEFAULT 'USED' NOT NULL, -- USED/DEL
    ADM_ID        INT NULL,                                          -- 管理员
    SUP_ID        INT NULL,                                          -- 供应商
    CONSTRAINT PK_AI_CREATE_VIDEO_LIB PRIMARY KEY (ACVL_ID)
);

-- ==========================================================
-- 9. AI_PROVIDER — AI 供应商
-- ==========================================================
CREATE TABLE AI_PROVIDER (
    AP_CODE   VARCHAR(20)  NOT NULL,      -- 供应商编码(PK)
    AP_NAME   NVARCHAR(100) NOT NULL,    -- 供应商名称
    AP_MEMO   NVARCHAR(MAX) NULL,        -- 备注
    AP_STATUS VARCHAR(4)   NOT NULL,     -- 状态(USED/DEL)
    AP_CDATE  DATETIME NOT NULL,                                     -- 创建时间
    AP_MDATE  DATETIME NOT NULL,                                     -- 修改时间
    ADM_ID    INT NULL,                                              -- 管理员
    CONSTRAINT PK_AI_PROVIDER PRIMARY KEY (AP_CODE)
);

-- ==========================================================
-- 10. AI_PROVIDER_MODEL — AI 模型 (含定价/并发/类型)
-- ==========================================================
CREATE TABLE AI_PROVIDER_MODEL (
    APM_CODE            VARCHAR(45)  NOT NULL, -- 模型编码(PK)
    AP_CODE             VARCHAR(20)  NOT NULL, -- 供应商编码(PK,FK→AI_PROVIDER)
    APM_NAME            NVARCHAR(100) NULL,   -- 模型名称
    APM_MEMO            NVARCHAR(MAX) NULL,   -- 备注
    APM_STATUS          VARCHAR(4)   NOT NULL, -- 状态(USED/DEL)
    APM_CDATE           DATETIME NOT NULL,                                -- 创建时间
    APM_MDATE           DATETIME NOT NULL,                                -- 修改时间
    ADM_ID              INT NULL,                                         -- 管理员
    APM_PRICE_TK_IN     MONEY NULL,                                       -- 输入价格(元/百万词元)-未命中
    APM_PRICE_TK_OUT    MONEY NULL,                                       -- 输出价格(元/百万词元)
    APM_PRICE_TK_CACHED MONEY NULL,                                       -- 输入价格(元/百万词元)-缓存命中
    APM_COIN_ID         INT NULL,                                         -- 币种ID
    APM_MAX_WINDOW      INT NULL,                                         -- 最大窗体Tokens
    APM_MAX_TOKEN       INT NULL,                                         -- 最大Token数
    APM_TYPE            VARCHAR(20)  DEFAULT 'AI_TP_CHAT' NOT NULL, -- 类型(AI_TP_CHAT/AI_TP_IMG/AI_TP_STT)
    APM_CONCURRENCY     INT NULL,                                         -- 并发数
    CONSTRAINT AI_PROVIDER_MODEL_PK PRIMARY KEY (APM_CODE, AP_CODE)
);

-- ==========================================================
-- 11. AI_PROVIDER_URL — API 地址和密钥
-- ==========================================================
CREATE TABLE AI_PROVIDER_URL (
    APU_UID         UNIQUEIDENTIFIER NOT NULL,                       -- 主键(UUID)
    AP_CODE         VARCHAR(20)  NOT NULL, -- 供应商编码(FK→AI_PROVIDER)
    APU_URL         VARCHAR(200) NULL,    -- API网址
    APU_KEY         VARCHAR(200) NULL,    -- 密钥
    APU_MEMO        NVARCHAR(MAX) NULL,   -- 备注
    APU_STATUS      VARCHAR(4)   NOT NULL, -- 状态(USED/DEL)
    APU_CDATE       DATETIME NOT NULL,                                -- 创建时间
    APU_MDATE       DATETIME NOT NULL,                                -- 修改时间
    ADM_ID          INT NULL,                                         -- 管理员
    APU_OWN_ID      VARCHAR(50)  NULL,    -- 归属ID
    APU_CONCURRENCY INT NULL,                                         -- 并发数
    CONSTRAINT PK_AI_PROVIDER_URL PRIMARY KEY (APU_UID)
);

-- ==========================================================
-- 12. AI_VOICE_CLONE — 声音复刻记录
-- ==========================================================
CREATE TABLE AI_VOICE_CLONE (
    AVC_ID          BIGINT IDENTITY(1,1) NOT NULL,                  -- 主键
    AVC_VOICE_ID    NVARCHAR(200) NULL,                              -- 音色 ID（API 返回）
    AVC_PROVIDER    VARCHAR(50)  NULL,                               -- provider 类型
    AVC_TARGET_MODEL VARCHAR(100) NULL,                              -- 绑定的合成模型
    AVC_PREFIX      NVARCHAR(100) NULL,                              -- 克隆时的前缀/名称
    AVC_AUDIO_URL   VARCHAR(500) NULL,                               -- 源音频 URL
    AVC_DESC        NVARCHAR(500) NULL,                              -- 备注
    AVC_STATUS      VARCHAR(20)  DEFAULT 'USED',                     -- USED/DELETED
    ADM_ID          INT NULL,                                        -- 管理员
    USR_ID          INT NULL,                                        -- 用户
    SUP_ID          INT NULL,                                        -- 供应商
    AVC_CDATE       DATETIME DEFAULT GETDATE(),                      -- 创建时间
    AVC_MDATE       DATETIME DEFAULT GETDATE(),                      -- 修改时间
    CONSTRAINT PK_AI_VOICE_CLONE PRIMARY KEY (AVC_ID)
);
CREATE NONCLUSTERED INDEX IDX_AVC_VOICE_ID ON AI_VOICE_CLONE(AVC_VOICE_ID, AVC_STATUS);
CREATE NONCLUSTERED INDEX IDX_AVC_PROVIDER ON AI_VOICE_CLONE(AVC_PROVIDER, AVC_STATUS);
CREATE NONCLUSTERED INDEX IDX_AVC_TARGET_MODEL ON AI_VOICE_CLONE(AVC_TARGET_MODEL, AVC_STATUS);
