package com.gdxsoft.ai.video.workflow.phase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 视频合成器 — 使用 ffmpeg 将多个分镜视频拼接为最终视频。
 *
 * <p>流程：
 * <ol>
 *   <li>下载各分镜视频到本地临时目录</li>
 *   <li>统一转码（分辨率/帧率/编码器）</li>
 *   <li>concat demuxer 无损拼接</li>
 *   <li>可选：叠加 BGM / 烧录字幕 / 添加转场</li>
 * </ol>
 *
 * <p>依赖：系统需安装 ffmpeg（{@code which ffmpeg} 检测）。
 *
 * @since 1.4.0
 */
public class VideoCompositor {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCompositor.class);

    private final ComposeOptions options;
    private Path workDir;

    public VideoCompositor(ComposeOptions options) {
        this.options = options;
    }

    /**
     * 合成最终视频。
     *
     * @param shotVideoUrls 分镜视频 URL 列表（按顺序）
     * @param outputDir     输出目录
     * @return 最终视频本地路径
     * @throws IOException 合成失败
     */
    public Path compose(List<String> shotVideoUrls, Path outputDir) throws IOException, InterruptedException {
        if (shotVideoUrls == null || shotVideoUrls.isEmpty()) {
            throw new IOException("没有分镜视频，无法合成");
        }

        // 0. 检测 ffmpeg
        checkFfmpeg();

        // 1. 创建工作目录
        workDir = Files.createTempDirectory(outputDir, "compose_");
        LOGGER.info("合成工作目录: {}", workDir);

        try {
            // 2. 下载视频
            List<Path> localFiles = new ArrayList<>();
            for (int i = 0; i < shotVideoUrls.size(); i++) {
                Path downloaded = downloadVideo(shotVideoUrls.get(i), workDir, "shot_" + i);
                if (downloaded != null) {
                    localFiles.add(downloaded);
                } else {
                    LOGGER.warn("分镜 {} 下载失败，跳过", i);
                }
            }

            if (localFiles.isEmpty()) {
                throw new IOException("所有分镜视频下载失败");
            }

            // 3. 统一转码
            List<Path> normalized = new ArrayList<>();
            for (int i = 0; i < localFiles.size(); i++) {
                Path n = normalizeVideo(localFiles.get(i), workDir, "norm_" + i);
                if (n != null) {
                    normalized.add(n);
                }
            }

            if (normalized.isEmpty()) {
                throw new IOException("所有视频转码失败");
            }

            // 4. 拼接
            Path concatFile = createConcatList(normalized, workDir);
            Path output = concatVideos(concatFile, outputDir);

            // 5. 可选增强
            if (options.getBgmPath() != null) {
                output = mixBgm(output, options.getBgmPath(), outputDir);
            }
            if (options.getSubtitlePath() != null) {
                output = burnSubtitles(output, options.getSubtitlePath(), outputDir);
            }

            LOGGER.info("合成完成: {}", output);
            return output;

        } finally {
            // 清理临时文件
            // cleanWorkDir();
        }
    }

    // ===== 步骤实现 =====

    /** 检测 ffmpeg */
    private void checkFfmpeg() throws IOException {
        try {
            exec(options.getFfmpegPath() + " -version", 5000);
        } catch (Exception e) {
            throw new IOException("ffmpeg 不可用: " + e.getMessage()
                    + "\n请安装 ffmpeg 或通过 ComposeOptions.ffmpegPath 指定路径");
        }
    }

    /** 下载视频到本地 */
    private Path downloadVideo(String url, Path dir, String name) {
        if (url == null || url.isEmpty()) return null;
        try {
            Path file = dir.resolve(name + ".mp4");
            var client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build();
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofFile(file));
            if (resp.statusCode() / 100 == 2) {
                LOGGER.info("下载完成: {} ({})", name, file);
                return file;
            }
            LOGGER.warn("下载失败 HTTP {}: {}", resp.statusCode(), url);
        } catch (Exception e) {
            LOGGER.warn("下载异常: {} → {}", url, e.getMessage());
        }
        return null;
    }

    /** 统一转码 */
    private Path normalizeVideo(Path input, Path dir, String name) throws IOException, InterruptedException {
        Path output = dir.resolve(name + ".mp4");
        String cmd = String.format(
                "%s -y -i %s -vf scale=%s -r %d -c:v %s -c:a %s -preset fast %s",
                options.getFfmpegPath(),
                input, options.getResolution(), options.getFps(),
                options.getVideoCodec(), options.getAudioCodec(),
                output);
        LOGGER.info("转码: {}", input.getFileName());
        int exitCode = exec(cmd, options.getTimeoutMs());
        if (exitCode == 0) return output;
        LOGGER.warn("转码失败 exit={}: {}", exitCode, input);
        return null;
    }

    /** 创建 concat 文件列表 */
    private Path createConcatList(List<Path> videos, Path dir) throws IOException {
        Path listFile = dir.resolve("concat_list.txt");
        StringBuilder sb = new StringBuilder();
        for (Path v : videos) {
            sb.append("file '").append(v.toAbsolutePath()).append("'\n");
        }
        Files.writeString(listFile, sb.toString());
        return listFile;
    }

    /** concat 拼接 */
    private Path concatVideos(Path listFile, Path outputDir) throws IOException, InterruptedException {
        Path output = outputDir.resolve("final_" + System.currentTimeMillis() + ".mp4");
        String cmd = String.format(
                "%s -y -f concat -safe 0 -i %s -c copy %s",
                options.getFfmpegPath(), listFile, output);
        LOGGER.info("拼接视频: {} 个片段", Files.readAllLines(listFile).size());
        int exitCode = exec(cmd, options.getTimeoutMs());
        if (exitCode != 0) {
            throw new IOException("视频拼接失败 exit=" + exitCode);
        }
        return output;
    }

    /** 叠加 BGM */
    private Path mixBgm(Path video, String bgmPath, Path outputDir) throws IOException, InterruptedException {
        Path output = outputDir.resolve("final_bgm_" + System.currentTimeMillis() + ".mp4");
        String vol = String.format("%.1f", options.getBgmVolume());
        String cmd = String.format(
                "%s -y -i %s -i %s -filter_complex \"[1:a]volume=%s[bgm];[0:a][bgm]amix=inputs=2:duration=first\" -c:v copy %s",
                options.getFfmpegPath(), video, bgmPath, vol, output);
        LOGGER.info("叠加 BGM: volume={}", vol);
        int exitCode = exec(cmd, options.getTimeoutMs());
        return exitCode == 0 ? output : video; // 失败则返回原视频
    }

    /** 烧录字幕 */
    private Path burnSubtitles(Path video, String srtPath, Path outputDir) throws IOException, InterruptedException {
        Path output = outputDir.resolve("final_sub_" + System.currentTimeMillis() + ".mp4");
        String cmd = String.format(
                "%s -y -i %s -vf subtitles=%s -c:a copy %s",
                options.getFfmpegPath(), video, srtPath, output);
        LOGGER.info("烧录字幕: {}", srtPath);
        int exitCode = exec(cmd, options.getTimeoutMs());
        return exitCode == 0 ? output : video;
    }

    /** 清理工作目录 */
    @SuppressWarnings("unused")
    private void cleanWorkDir() {
        if (workDir != null) {
            try {
                Files.walk(workDir).sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            } catch (Exception e) {
                LOGGER.warn("清理工作目录异常: {}", e.getMessage());
            }
        }
    }

    // ===== ProcessBuilder 执行 (参考 Tool.executeCommand) =====

    private int exec(String cmd, long timeoutMs) throws IOException, InterruptedException {
        String[] cmdArray = parseCommand(cmd);
        ProcessBuilder pb = new ProcessBuilder(cmdArray);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder out = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (out.length() < 10000) out.append(line).append("\n");
                }
            } catch (IOException ignore) {}
        });
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(1000, TimeUnit.MILLISECONDS);
            LOGGER.error("ffmpeg 超时 ({}ms)\n{}", timeoutMs, out);
            return -1;
        }

        int exitCode = process.exitValue();
        reader.join(2000);
        if (exitCode != 0) {
            LOGGER.warn("ffmpeg exit={}\n{}", exitCode, out);
        }
        return exitCode;
    }

    /** 简易命令行解析（处理引号） */
    private String[] parseCommand(String cmd) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (char c : cmd.toCharArray()) {
            if (c == '"') { inQuote = !inQuote; continue; }
            if (c == ' ' && !inQuote) {
                if (sb.length() > 0) { tokens.add(sb.toString()); sb.setLength(0); }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    /** 合成结果 */
    public static class ComposeResult {
        private Path outputPath;
        private String outputUrl;
        private long fileSizeBytes;
        private double durationSeconds;

        public Path getOutputPath() { return outputPath; }
        public void setOutputPath(Path v) { this.outputPath = v; }
        public String getOutputUrl() { return outputUrl; }
        public void setOutputUrl(String v) { this.outputUrl = v; }
        public long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(long v) { this.fileSizeBytes = v; }
        public double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(double v) { this.durationSeconds = v; }

        public JSONObject toJson() {
            var j = new JSONObject();
            j.put("outputPath", outputPath != null ? outputPath.toString() : "");
            j.put("fileSizeBytes", fileSizeBytes);
            j.put("durationSeconds", durationSeconds);
            return j;
        }
    }
}
