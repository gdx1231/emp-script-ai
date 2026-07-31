package com.gdxsoft.ai.video.workflow.phase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.img.ImgConcurrency;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard;

/**
 * Phase2：图片素材生成 — 为所有人物和环境批量生成图片。
 *
 * <p>流程：
 * <ol>
 *   <li>读取 storyboard 中的 characters 和 environments</li>
 *   <li>为每个创建 {@link ImgRequest} 和对应的 AI_WF_TASK 记录</li>
 *   <li>使用 {@link ImgConcurrency} 并发生成（信号量限流）</li>
 *   <li>下载图片到本地输出目录</li>
 *   <li>保存资产到 AI_WF_ASSET + 回填 imageUrl 到 storyboard</li>
 *   <li>单张失败不影响整批（部分失败仍推进后续阶段）</li>
 * </ol>
 *
 * @since 1.4.0
 */
public class MaterialsPhase implements IPhaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialsPhase.class);

    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        WorkflowDb db = ctx.getDb();
        long instanceId = ctx.getInstanceId();
        PhaseDef phaseDef = ctx.getConfig().getPhase("materials");
        Storyboard sb = ctx.getStoryboard();

        if (phaseDef == null) {
            throw new IllegalStateException("工作流配置中缺少 'materials' 阶段定义");
        }
        if (sb == null) {
            throw new IllegalStateException("storyboard 为空，请先执行 planning 阶段");
        }

        LOGGER.info("[MaterialsPhase] 开始素材生成, WFI_ID={}, characters={}, environments={}",
                instanceId, sb.getCharacterCount(), sb.getEnvironmentCount());

        // 1. 准备输出目录
        Path outputDir = prepareOutputDir(ctx);

        // 2. 获取 API 凭证
        WorkflowContext.ApiCredential cred = ctx.getApiCredential(phaseDef.getProvider());
        if (cred == null || cred.getKey() == null || cred.getKey().isEmpty()) {
            throw new IllegalStateException("图片供应商 " + phaseDef.getProvider() + " 的 API Key 未配置");
        }

        // 3. 构建图片请求列表
        List<MaterialRequest> matReqs = buildMaterialRequests(sb, phaseDef);

        if (matReqs.isEmpty()) {
            LOGGER.warn("[MaterialsPhase] 没有需要生成的图片素材");
            db.updateInstanceProgress(instanceId, "materials", 40);
            db.updateInstanceStatus(instanceId, WorkflowDb.STATUS_GENERATING);
            return;
        }

        // 4. 为每个请求创建 AI_WF_TASK
        List<Long> taskIds = new ArrayList<>();
        for (int i = 0; i < matReqs.size(); i++) {
            MaterialRequest mr = matReqs.get(i);
            JSONObject input = new JSONObject();
            input.put("refType", mr.refType);
            input.put("refName", mr.refName);
            input.put("prompt", mr.imgPrompt);
            input.put("size", phaseDef.getSize());

            long taskId = db.createTask(instanceId, "material_img", mr.taskName, i,
                    "materials", input.toString());
            taskIds.add(taskId);
        }

        db.updateInstanceProgress(instanceId, "materials", 25);

        // 5. 构建 ImgRequest 列表
        List<ImgRequest> imgRequests = new ArrayList<>();
        for (MaterialRequest mr : matReqs) {
            imgRequests.add(new ImgRequest(
                    new ImgOptions(mr.imgPrompt)
                            .size(phaseDef.getSize())
                            .n(1)
                            .responseFormat("url")));
        }

        // 6. 并发批量生成
        int concurrency = phaseDef.getConcurrency() > 0 ? phaseDef.getConcurrency() : 3;
        ImgConcurrency concur = ImgConcurrency.of(phaseDef.getProvider())
                .apiKey(cred.getKey())
                .maxConcurrency(concurrency)
                .maxRetries(ctx.getConfig().getLimits().getMaxRetries());

        int[] successCount = {0};
        int[] failCount = {0};

        concur.generateAll(imgRequests, (index, response, error) -> {
            MaterialRequest mr = matReqs.get(index);
            long taskId = taskIds.get(index);

            if (error != null) {
                failCount[0]++;
                LOGGER.error("[MaterialsPhase] {} 生成失败: {}", mr.taskName, error.getMessage());
                db.updateTaskError(taskId, error.getMessage());
                return;
            }

            try {
                // 下载到本地
                String imgUrl = response.getFirstImage().getUrl();
                Path localPath = downloadImage(imgUrl, outputDir, mr.refType, mr.refName);

                // 保存资产
                JSONObject metadata = new JSONObject();
                metadata.put("size", phaseDef.getSize());
                if (localPath != null) metadata.put("localPath", localPath.toString());

                db.saveAsset(instanceId, taskId, mr.refType + "_img",
                        mr.refType, mr.refName, mr.taskName,
                        imgUrl, localPath != null ? localPath.toString() : null,
                        metadata.toString());

                // 更新任务状态
                JSONObject output = new JSONObject();
                output.put("url", imgUrl);
                if (localPath != null) output.put("localPath", localPath.toString());
                db.updateTaskStatus(taskId, WorkflowDb.TASK_SUCCEEDED, output.toString());

                // 回填 storyboard
                if ("character".equals(mr.refType)) {
                    Storyboard.StoryCharacter c = sb.findCharacter(mr.refName);
                    if (c != null) c.setImageUrl(imgUrl);
                } else if ("environment".equals(mr.refType)) {
                    Storyboard.StoryEnvironment e = sb.findEnvironment(mr.refName);
                    if (e != null) e.setImageUrl(imgUrl);
                }

                // 缓存到上下文（供后续阶段快速查找）
                ctx.putImageAsset(mr.refType + ":" + mr.refName, imgUrl,
                        localPath != null ? localPath.toString() : null);

                // 释放 base64 内存
                for (ImgResponse.GeneratedImage gi : response.getImages()) {
                    gi.release();
                }

                successCount[0]++;
                LOGGER.info("[MaterialsPhase] {} 生成成功 → {}", mr.taskName, imgUrl);

            } catch (Exception e) {
                failCount[0]++;
                LOGGER.error("[MaterialsPhase] {} 保存失败: {}", mr.taskName, e.getMessage());
                db.updateTaskError(taskId, e.getMessage());
            }
        });

        // 7. 检查结果
        int total = matReqs.size();
        if (successCount[0] == 0) {
            throw new IOException("全部 " + total + " 张图片素材生成失败");
        }

        // 8. 更新故事板（URL 已回填，重新持久化）
        db.saveStoryboard(instanceId, sb.toJson().toString());
        db.updateInstanceProgress(instanceId, "materials", 40);
        db.updateInstanceStatus(instanceId, WorkflowDb.STATUS_GENERATING);

        LOGGER.info("[MaterialsPhase] 素材生成完成: 成功 {}/{}, 失败 {}/{}",
                successCount[0], total, failCount[0], total);

        if (failCount[0] > 0) {
            LOGGER.warn("[MaterialsPhase] {} 张图片生成失败，将使用占位符继续后续阶段", failCount[0]);
        }
    }

    // ===== 内部方法 =====

    /** 素材请求描述 */
    static class MaterialRequest {
        String refType;    // "character" / "environment"
        String refName;    // 名称
        String imgPrompt;  // 英文绘图提示词
        String taskName;   // 任务显示名（如 "人物图:小明"）
    }

    /** 从 storyboard 构建素材请求列表 */
    private List<MaterialRequest> buildMaterialRequests(Storyboard sb, PhaseDef phaseDef) {
        List<MaterialRequest> list = new ArrayList<>();

        for (Storyboard.StoryCharacter c : sb.getCharacters()) {
            MaterialRequest mr = new MaterialRequest();
            mr.refType = "character";
            mr.refName = c.getName();
            mr.imgPrompt = c.getImgPrompt();
            mr.taskName = "人物图:" + c.getName();
            list.add(mr);
        }

        for (Storyboard.StoryEnvironment e : sb.getEnvironments()) {
            MaterialRequest mr = new MaterialRequest();
            mr.refType = "environment";
            mr.refName = e.getName();
            mr.imgPrompt = e.getImgPrompt();
            mr.taskName = "环境图:" + e.getName();
            list.add(mr);
        }

        return list;
    }

    /** 准备输出目录 */
    private Path prepareOutputDir(WorkflowContext ctx) throws IOException {
        String baseDir = ctx.getConfig().getOutputBaseDir();
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = ".";
        }
        Path dir = Paths.get(baseDir, "wf-" + ctx.getInstanceId(), "materials");
        Files.createDirectories(dir);
        return dir;
    }

    /** 下载图片到本地 */
    private Path downloadImage(String url, Path outputDir, String refType, String refName) {
        if (url == null || url.isEmpty()) return null;
        try {
            String safeName = (refType + "_" + refName)
                    .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]", "_");
            Path file = outputDir.resolve(safeName + ".png");

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<java.nio.file.Path> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofFile(file));
            if (resp.statusCode() / 100 == 2) {
                return file;
            }
            LOGGER.warn("下载图片失败 HTTP {}: {}", resp.statusCode(), url);
        } catch (Exception e) {
            LOGGER.warn("下载图片异常: {} → {}", url, e.getMessage());
        }
        return null;
    }
}
