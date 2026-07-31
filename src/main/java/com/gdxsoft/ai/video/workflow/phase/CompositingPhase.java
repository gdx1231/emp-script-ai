package com.gdxsoft.ai.video.workflow.phase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard;
import com.gdxsoft.ai.video.workflow.model.Storyboard.StoryShot;

/**
 * Phase4：视频合成 — 使用 ffmpeg 将分镜视频拼接为最终视频。
 *
 * @since 1.4.0
 */
public class CompositingPhase implements IPhaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositingPhase.class);

    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        WorkflowDb db = ctx.getDb();
        long instanceId = ctx.getInstanceId();
        PhaseDef phaseDef = ctx.getConfig().getPhase("compositing");
        Storyboard sb = ctx.getStoryboard();

        if (phaseDef == null || sb == null) return;

        List<StoryShot> shots = sb.getShots();
        if (shots.isEmpty()) {
            LOGGER.info("[CompositingPhase] 无分镜视频，跳过合成");
            return;
        }

        LOGGER.info("[CompositingPhase] 开始合成, WFI_ID={}, shots={}", instanceId, shots.size());

        // 创建 task
        long taskId = db.createTask(instanceId, "video_compose",
                "最终合成", 0, "compositing", null);
        db.markTaskRunning(taskId);
        db.updateInstanceProgress(instanceId, "compositing", 65);

        try {
            // 构建合成选项
            ComposeOptions composeOpts = new ComposeOptions();
            composeOpts.ffmpegPath(ctx.getConfig().getFfmpegPath() != null
                    ? ctx.getConfig().getFfmpegPath() : "ffmpeg");

            if (phaseDef.getFfmpeg() != null) {
                String res = phaseDef.getFfmpegString("resolution");
                if (res != null) composeOpts.resolution(res);
                composeOpts.fps(phaseDef.getFfmpegInt("fps", 30));
                String vc = phaseDef.getFfmpegString("videoCodec");
                if (vc != null) composeOpts.videoCodec(vc);
                String ac = phaseDef.getFfmpegString("audioCodec");
                if (ac != null) composeOpts.audioCodec(ac);
                composeOpts.transitions(phaseDef.getFfmpegBoolean("transitions", false));
                composeOpts.bgmVolume(phaseDef.getFfmpegDouble("bgmVolume", 0.3));
                composeOpts.bgmPath(phaseDef.getFfmpegString("bgmPath"));
                composeOpts.subtitlePath(phaseDef.getFfmpegString("subtitlePath"));
            }

            // 收集视频 URL
            List<String> videoUrls = new ArrayList<>();
            for (StoryShot shot : shots) {
                WorkflowContext.ShotAsset asset = ctx.getShotAsset(shot.getIndex());
                if (asset != null && asset.getVideoUrl() != null) {
                    videoUrls.add(asset.getVideoUrl());
                }
            }

            if (videoUrls.isEmpty()) {
                throw new java.io.IOException("没有可用的分镜视频 URL");
            }

            // 输出目录
            Path outputDir = Paths.get(ctx.getConfig().getOutputBaseDir(),
                    "wf-" + instanceId, "final");
            Files.createDirectories(outputDir);

            // 执行合成
            VideoCompositor compositor = new VideoCompositor(composeOpts);
            Path finalPath = compositor.compose(videoUrls, outputDir);

            // 保存结果
            long fileSize = Files.size(finalPath);
            ctx.setFinalVideoPath(finalPath.toString());

            JSONObject metadata = new JSONObject();
            metadata.put("fileSize", fileSize);
            metadata.put("shotCount", videoUrls.size());

            db.saveAsset(instanceId, taskId, "final_video",
                    "workflow", ctx.getConfig().getName(),
                    "最终视频", null, finalPath.toString(), metadata.toString());

            JSONObject output = new JSONObject();
            output.put("finalPath", finalPath.toString());
            output.put("fileSize", fileSize);
            output.put("shotCount", videoUrls.size());
            db.updateTaskStatus(taskId, WorkflowDb.TASK_SUCCEEDED, output.toString());

            db.updateInstanceProgress(instanceId, "compositing", 95);

            LOGGER.info("[CompositingPhase] 合成完成: {} ({} bytes, {} shots)",
                    finalPath, fileSize, videoUrls.size());

        } catch (Exception e) {
            LOGGER.error("[CompositingPhase] 合成失败: {}", e.getMessage());
            db.updateTaskError(taskId, e.getMessage());
            throw e;
        }
    }
}
