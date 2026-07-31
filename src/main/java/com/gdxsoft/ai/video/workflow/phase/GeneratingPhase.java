package com.gdxsoft.ai.video.workflow.phase;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.VideoOptions;
import com.gdxsoft.ai.video.VideoRequest;
import com.gdxsoft.ai.video.VideoResponse;
import com.gdxsoft.ai.video.VideoTaskStatus;
import com.gdxsoft.ai.video.VideoTaskSubmit;
import com.gdxsoft.ai.video.providers.doubao.DoubaoVideoProvider;
import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard;
import com.gdxsoft.ai.video.workflow.model.Storyboard.StoryShot;

/**
 * Phase3：分镜视频生成 — 逐分镜生成视频，支持尾帧续接保证画面连续。
 *
 * <p>两种模式（由 workflow.json {@code chainShots} 控制）：
 * <ul>
 *   <li><b>续接模式</b>（chainShots=true, concurrency=1）：Shot N 尾帧 → Shot N+1 首帧，串行执行</li>
 *   <li><b>独立模式</b>（chainShots=false, concurrency>1）：各 Shot 独立生成，可并行</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@code MaterialsPhase} — 需 ctx.imageAssets 中有素材 URL</li>
 *   <li>{@code DoubaoVideoProvider} — 非阻塞 submit/poll + returnLastFrame</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class GeneratingPhase implements IPhaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratingPhase.class);

    /** 轮询间隔（毫秒） */
    private static final long POLL_DELAY_MS = 5000;

    /** 最大轮询次数（5s * 240 = 20 min） */
    private static final int MAX_POLL_COUNT = 240;

    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        WorkflowDb db = ctx.getDb();
        long instanceId = ctx.getInstanceId();
        PhaseDef phaseDef = ctx.getConfig().getPhase("generating");
        Storyboard sb = ctx.getStoryboard();

        if (phaseDef == null) throw new IllegalStateException("配置缺少 'generating' 阶段");
        if (sb == null) throw new IllegalStateException("storyboard 为空");

        int shotCount = sb.getShotCount();
        if (shotCount == 0) {
            LOGGER.warn("[GeneratingPhase] 无分镜，直接跳过");
            db.updateInstanceProgress(instanceId, "generating", 60);
            db.updateInstanceStatus(instanceId, WorkflowDb.STATUS_COMPOSITING);
            return;
        }

        LOGGER.info("[GeneratingPhase] 开始视频生成, WFI_ID={}, shots={}, chain={}",
                instanceId, shotCount, phaseDef.isChainShots());

        // 1. API 凭证
        WorkflowContext.ApiCredential cred = ctx.getApiCredential(phaseDef.getProvider());
        if (cred == null || cred.getKey() == null || cred.getKey().isEmpty()) {
            throw new IllegalStateException("视频供应商 " + phaseDef.getProvider() + " API Key 未配置");
        }

        // 2. 创建 provider
        DoubaoVideoProvider vp = new DoubaoVideoProvider();
        vp.setApiKey(cred.getKey());
        if (cred.getUrl() != null && !cred.getUrl().isEmpty()) {
            vp.setApiUrl(cred.getUrl());
        }
        vp.setMaxPollCount(MAX_POLL_COUNT);
        vp.setPollDelayMs(POLL_DELAY_MS);

        // 3. 默认参数
        String defaultAspectRatio = phaseDef.getDefaultAspectRatio() != null
                ? phaseDef.getDefaultAspectRatio() : "16:9";
        String defaultResolution = phaseDef.getDefaultResolution() != null
                ? phaseDef.getDefaultResolution() : "720p";
        int maxDuration = phaseDef.getMaxDuration() > 0 ? phaseDef.getMaxDuration() : 15;
        String model = phaseDef.getModel();

        // 4. 预先创建所有 task（含依赖链）
        List<Long> taskIds = new ArrayList<>();
        for (int i = 0; i < shotCount; i++) {
            StoryShot shot = sb.getShot(i + 1); // index 从 1 开始
            if (shot == null) continue;

            JSONObject input = buildTaskInput(shot);
            Long depId = null;
            if (phaseDef.isChainShots() && i > 0 && !taskIds.isEmpty()) {
                depId = taskIds.get(i - 1); // 依赖前一个 shot
            }

            long taskId;
            if (depId != null) {
                taskId = db.createTaskWithDep(instanceId, "shot_video",
                        "分镜" + shot.getIndex() + "视频", i, "generating",
                        input.toString(), depId);
            } else {
                taskId = db.createTask(instanceId, "shot_video",
                        "分镜" + shot.getIndex() + "视频", i, "generating",
                        input.toString());
            }
            taskIds.add(taskId);
        }

        db.updateInstanceProgress(instanceId, "generating", 45);

        // 5. 逐分镜生成
        String lastFrameUrl = null;
        int successCount = 0;

        for (int i = 0; i < shotCount; i++) {
            StoryShot shot = sb.getShot(i + 1);
            if (shot == null) continue;

            long taskId = taskIds.get(i);
            db.markTaskRunning(taskId);

            try {
                // 构建 VideoOptions
                VideoOptions opts = new VideoOptions(shot.getVideoPrompt());
                opts.duration(Math.min(shot.getDuration(), maxDuration));
                opts.aspectRatio(shot.getAspectRatio() != null
                        ? shot.getAspectRatio() : defaultAspectRatio);
                opts.resolution(shot.getResolution() != null
                        ? shot.getResolution() : defaultResolution);
                opts.returnLastFrame(phaseDef.isChainShots());

                if (shot.getCameraMovement() != null) {
                    opts.cameraMovement(shot.getCameraMovement());
                }
                if (model != null) {
                    opts.model(model);
                }
                if (shot.getDialogue() != null && !shot.getDialogue().isEmpty()) {
                    opts.generateAudio(true);
                }

                // 参考图：续接模式用尾帧，独立模式用素材图
                if (phaseDef.isChainShots() && i > 0 && lastFrameUrl != null) {
                    opts.addRefImageUrl(lastFrameUrl);
                    LOGGER.info("[GeneratingPhase] 分镜{} 使用前置尾帧续接", shot.getIndex());
                } else {
                    addMaterialUrls(opts, shot, ctx);
                }

                // 提交 + 轮询
                VideoRequest vReq = new VideoRequest(opts);
                LOGGER.info("[GeneratingPhase] 提交分镜{}/{}: prompt='{}'",
                        i + 1, shotCount, shot.getVideoPrompt().substring(0, Math.min(60,
                                shot.getVideoPrompt().length())));

                VideoTaskSubmit submit = vp.submitTask(vReq);
                String remoteId = submit.getTaskId();
                db.updateTaskRemoteId(taskId, phaseDef.getProvider(), model, remoteId);

                // 轮询
                VideoTaskStatus st;
                int pollCount = 0;
                do {
                    Thread.sleep(POLL_DELAY_MS);
                    st = vp.pollTask(remoteId, opts);
                    pollCount++;
                    db.updateTaskRemoteStatus(taskId, st.getStatus());

                    if (pollCount % 6 == 0) { // 每 30s 打印进度
                        LOGGER.info("[GeneratingPhase] 分镜{} 轮询中... {}/{} ({})",
                                shot.getIndex(), pollCount, MAX_POLL_COUNT, st.getStatus());
                    }
                } while (st.isProcessing() && pollCount < MAX_POLL_COUNT);

                if (st.isProcessing()) {
                    throw new java.io.IOException("分镜" + shot.getIndex()
                            + " 视频生成超时 (" + (MAX_POLL_COUNT * POLL_DELAY_MS / 1000) + "s)");
                }
                if (st.isFailed()) {
                    throw new java.io.IOException("分镜" + shot.getIndex() + " 视频生成失败: " + st.getError());
                }

                // 成功
                VideoResponse resp = st.getResponse();
                String videoUrl = resp.getFirstVideo() != null
                        ? resp.getFirstVideo().getUrl() : null;
                String lfUrl = resp.getLastFrameUrl();

                // 缓存尾帧
                if (lfUrl != null && !lfUrl.isEmpty()) {
                    lastFrameUrl = lfUrl;
                }

                // 保存资产
                db.saveAsset(instanceId, taskId, "shot_video",
                        "shot", "shot_" + shot.getIndex(),
                        "分镜" + shot.getIndex() + "视频",
                        videoUrl, null,
                        new JSONObject()
                                .put("duration", shot.getDuration())
                                .put("resolution", opts.getResolution())
                                .toString());

                if (lfUrl != null && !lfUrl.isEmpty()) {
                    db.saveAsset(instanceId, taskId, "last_frame",
                            "shot", "shot_" + shot.getIndex(),
                            "分镜" + shot.getIndex() + "尾帧",
                            lfUrl, null, null);
                }

                // 缓存到上下文
                ctx.putShotAsset(shot.getIndex(), videoUrl, lfUrl, null);

                // 更新 task
                JSONObject output = new JSONObject();
                output.put("videoUrl", videoUrl);
                output.put("lastFrameUrl", lfUrl);
                db.updateTaskStatus(taskId, WorkflowDb.TASK_SUCCEEDED, output.toString());

                // 回填 storyboard
                shot.setVideoUrl(videoUrl);

                successCount++;
                int progress = 45 + (int) ((double) (i + 1) / shotCount * 15);
                db.updateInstanceProgress(instanceId, "generating", progress);

                LOGGER.info("[GeneratingPhase] 分镜{} 完成 ({}/{}): video={}",
                        shot.getIndex(), i + 1, shotCount, videoUrl);

            } catch (Exception e) {
                LOGGER.error("[GeneratingPhase] 分镜{} 失败: {}", shot.getIndex(), e.getMessage());
                db.updateTaskError(taskId, e.getMessage());

                if (phaseDef.isChainShots()) {
                    // 续接模式：一个失败后续全部无法继续
                    throw new java.io.IOException(
                            "分镜" + shot.getIndex() + " 失败，续接链中断: " + e.getMessage(), e);
                }
                // 独立模式：继续下一个
            }
        }

        // 6. 结果检查
        if (successCount == 0) {
            throw new java.io.IOException("全部 " + shotCount + " 个分镜视频生成失败");
        }

        // 7. 持久化 storyboard（含 videoUrl）
        db.saveStoryboard(instanceId, sb.toJson().toString());
        db.updateInstanceProgress(instanceId, "generating", 60);
        db.updateInstanceStatus(instanceId, WorkflowDb.STATUS_COMPOSITING);

        LOGGER.info("[GeneratingPhase] 视频生成完成: 成功 {}/{}, 尾帧续接={}",
                successCount, shotCount, phaseDef.isChainShots());
    }

    /** 添加素材参考图 URL 到 VideoOptions */
    private void addMaterialUrls(VideoOptions opts, StoryShot shot, WorkflowContext ctx) {
        // 人物图
        if (shot.getCharacterRefs() != null) {
            for (String ref : shot.getCharacterRefs()) {
                Storyboard.StoryCharacter c = ctx.getStoryboard().findCharacter(ref);
                if (c != null && c.getImageUrl() != null) {
                    opts.addRefImageUrl(c.getImageUrl());
                }
            }
        }
        // 环境图
        if (shot.getEnvironmentRef() != null) {
            Storyboard.StoryEnvironment e = ctx.getStoryboard()
                    .findEnvironment(shot.getEnvironmentRef());
            if (e != null && e.getImageUrl() != null) {
                opts.addRefImageUrl(e.getImageUrl());
            }
        }
        // 兜底：用所有素材图
        if (opts.getRefImageUrls() == null || opts.getRefImageUrls().isEmpty()) {
            for (Storyboard.StoryCharacter c : ctx.getStoryboard().getCharacters()) {
                if (c.getImageUrl() != null) opts.addRefImageUrl(c.getImageUrl());
            }
            for (Storyboard.StoryEnvironment env : ctx.getStoryboard().getEnvironments()) {
                if (env.getImageUrl() != null) opts.addRefImageUrl(env.getImageUrl());
            }
        }
    }

    private JSONObject buildTaskInput(StoryShot shot) {
        JSONObject j = new JSONObject();
        j.put("index", shot.getIndex());
        j.put("description", shot.getDescription());
        j.put("videoPrompt", shot.getVideoPrompt());
        j.put("duration", shot.getDuration());
        j.put("cameraMovement", shot.getCameraMovement());
        j.put("hasDialogue", shot.getDialogue() != null && !shot.getDialogue().isEmpty());
        return j;
    }
}
