package com.gdxsoft.ai.video.workflow.phase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.tts.TtsClient;
import com.gdxsoft.ai.tts.TtsRequest;
import com.gdxsoft.ai.tts.TtsResponse;
import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard.StoryShot;

/**
 * 可选阶段：TTS 语音合成 — 为分镜中的旁白/台词生成音频。
 *
 * <p>在 compositing 之前执行，产物可供 VideoCompositor 混入。
 *
 * @since 1.4.0
 */
public class TtsPhase implements IPhaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TtsPhase.class);

    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        PhaseDef phaseDef = ctx.getConfig().getPhaseByType("tts");
        if (phaseDef == null) {
            LOGGER.info("[TtsPhase] 未配置 TTS 阶段，跳过");
            return;
        }

        WorkflowDb db = ctx.getDb();
        long instanceId = ctx.getInstanceId();

        WorkflowContext.ApiCredential cred = ctx.getApiCredential(phaseDef.getProvider());
        if (cred == null || cred.getKey() == null) {
            LOGGER.warn("[TtsPhase] TTS API Key 未配置，跳过");
            return;
        }

        var sb = ctx.getStoryboard();
        if (sb == null) return;

        // 输出目录
        Path outputDir = Paths.get(ctx.getConfig().getOutputBaseDir(),
                "wf-" + instanceId, "audio");
        Files.createDirectories(outputDir);

        // 为每个有台词的 shot 生成音频
        for (StoryShot shot : sb.getShots()) {
            String text = getSynthesisText(shot);
            if (text == null || text.isEmpty()) continue;

            long taskId = db.createTask(instanceId, "tts",
                    "分镜" + shot.getIndex() + "配音", shot.getIndex(),
                    "tts", new JSONObject().put("text", text).toString());
            db.markTaskRunning(taskId);

            try {
                TtsClient client = TtsClient.of(phaseDef.getProvider());
                client.getProvider().setApiKey(cred.getKey());
                if (cred.getUrl() != null && !cred.getUrl().isEmpty()) {
                    client.getProvider().setApiUrl(cred.getUrl());
                }

                TtsResponse resp = client.synthesize(new TtsRequest(text));
                if (resp.getAudio() != null) {
                    Path audioFile = outputDir.resolve("shot_" + shot.getIndex() + ".wav");
                    resp.save(audioFile);
                    db.saveAsset(instanceId, taskId, "tts_audio",
                            "shot", "shot_" + shot.getIndex(),
                            "分镜" + shot.getIndex() + "音频",
                            null, audioFile.toString(), null);
                    db.updateTaskStatus(taskId, WorkflowDb.TASK_SUCCEEDED,
                            new JSONObject().put("audioPath", audioFile.toString()).toString());
                }
            } catch (Exception e) {
                LOGGER.warn("[TtsPhase] 分镜{} TTS 失败: {}", shot.getIndex(), e.getMessage());
                db.updateTaskError(taskId, e.getMessage());
            }
        }
    }

    private String getSynthesisText(StoryShot shot) {
        StringBuilder sb = new StringBuilder();
        if (shot.getNarration() != null && !shot.getNarration().isEmpty()) {
            sb.append(shot.getNarration());
        }
        if (shot.getDialogue() != null && !shot.getDialogue().isEmpty()) {
            if (sb.length() > 0) sb.append("。");
            sb.append(shot.getDialogue());
        }
        return sb.toString();
    }
}
