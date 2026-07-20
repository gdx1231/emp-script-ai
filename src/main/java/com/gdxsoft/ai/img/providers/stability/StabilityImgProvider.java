package com.gdxsoft.ai.img.providers.stability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.ai.img.ImgResponse.GeneratedImage;

/**
 * Stability AI image generation provider.
 * <p>
 * Uses Stability AI's v2beta stable-image generate API with multipart/form-data.
 * Default endpoint: {@code https://api.stability.ai/v2beta/stable-image/generate/core}
 *
 * @since 1.2.0
 */
public class StabilityImgProvider extends ImgProviderBase {
    public static final String DEFAULT_URL = "https://api.stability.ai/v2beta/stable-image/generate/core";
    public static final String DEFAULT_MODEL = "stable-diffusion-xl";

    public StabilityImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.STABILITY; }

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Stability AI requires an API key (setApiKey)");
        }
        byte[] boundary = buildMultipartBoundary();
        byte[] bodyBytes = buildMultipartBody(request.getOptions(), boundary);

        // Stream response to temp file to avoid OOM on large images
        Path tmpFile = Files.createTempFile("stability_img_", ".bin");
        try {
            postMultipartToFile(bodyBytes, new String(boundary, StandardCharsets.UTF_8), tmpFile);
            return parseResponse(tmpFile, request.getOptions());
        } finally {
            try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
        }
    }

    @Override
    public String curl(ImgRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Accept: image/*' \\\n");
        sb.append("  -F 'prompt=").append(request.getOptions().getPrompt().replace("'", "'\\''")).append("' \\\n");
        sb.append("  -F 'output_format=jpeg'");
        if (request.getOptions().getNegativePrompt() != null) {
            sb.append(" \\\n  -F 'negative_prompt=")
                    .append(request.getOptions().getNegativePrompt().replace("'", "'\\''")).append("'");
        }
        if (request.getOptions().getSeed() != null) {
            sb.append(" \\\n  -F 'seed=").append(request.getOptions().getSeed()).append("'");
        }
        return sb.toString();
    }

    /**
     * Build the multipart form-data body for Stability AI.
     * Visible for testing.
     */
    public byte[] buildMultipartBody(ImgOptions opts, byte[] boundary) throws IOException {
        String bStr = new String(boundary, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();

        appendPart(sb, bStr, "prompt", opts.getPrompt());
        appendPart(sb, bStr, "output_format", "jpeg"); // Stability returns image bytes

        if (opts.getNegativePrompt() != null) {
            appendPart(sb, bStr, "negative_prompt", opts.getNegativePrompt());
        }
        if (opts.getSeed() != null) {
            appendPart(sb, bStr, "seed", String.valueOf(opts.getSeed()));
        }
        if (opts.getSteps() != null) {
            appendPart(sb, bStr, "steps", String.valueOf(opts.getSteps()));
        }
        if (opts.getSize() != null) {
            // Stability uses aspect_ratio, try to convert size to aspect ratio
            String ratio = sizeToAspectRatio(opts.getSize());
            if (ratio != null) {
                appendPart(sb, bStr, "aspect_ratio", ratio);
            }
        }
        if (opts.getStyle() != null) {
            appendPart(sb, bStr, "style_preset", opts.getStyle());
        }

        sb.append("--").append(bStr).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build a random multipart boundary.
     */
    public byte[] buildMultipartBoundary() {
        return UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse the Stability AI response from a temp file (memory-safe).
     */
    public ImgResponse parseResponse(Path tmpFile, ImgOptions opts) throws IOException {
        byte[] bytes = Files.readAllBytes(tmpFile);
        String responseBody = new String(bytes, StandardCharsets.UTF_8);

        // Try JSON first (error case or JSON-wrapped image)
        try {
            JSONObject root = new JSONObject(responseBody);
            if (root.has("errors") || root.has("message")) {
                String msg = root.optString("message",
                        root.optJSONArray("errors") != null
                                ? root.getJSONArray("errors").toString()
                                : responseBody);
                throw new RuntimeException("Stability AI error: " + msg);
            }
            if (root.has("image")) {
                String b64 = root.getString("image");
                List<GeneratedImage> images = new ArrayList<>();
                images.add(new GeneratedImage(null, b64, root.optString("revised_prompt", null)));
                return new ImgResponse(images, null, root.optString("revised_prompt", null),
                        opts.getModel(), null, root);
            }
            return new ImgResponse(new ArrayList<>(), null, null, opts.getModel(), null, root);
        } catch (org.json.JSONException notJson) {
            // Raw image bytes — generate a download URL pattern (caller saves via writeToFile)
            // Store as base64 but keep it manageable via release()
            String b64 = Base64.getEncoder().encodeToString(bytes);
            List<GeneratedImage> images = new ArrayList<>();
            images.add(new GeneratedImage(null, b64, null));
            return new ImgResponse(images, null, null, opts.getModel(), null, null);
        }
    }

    /**
     * Parse from String (backward-compatible, used by tests).
     */
    public ImgResponse parseResponse(String responseBody, ImgOptions opts) {
        try {
            Path tmp = Files.createTempFile("stability_parse_", ".bin");
            Files.write(tmp, responseBody.getBytes(StandardCharsets.UTF_8));
            try {
                return parseResponse(tmp, opts);
            } finally {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Stability response", e);
        }
    }

    private void appendPart(StringBuilder sb, String boundary, String name, String value) {
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n");
        sb.append("\r\n");
        sb.append(value).append("\r\n");
    }

    /**
     * Convert a size string like "1024x1024" to a Stability AI aspect ratio.
     */
    private String sizeToAspectRatio(String size) {
        if (size == null) return null;
        return switch (size) {
            case "1024x1024", "1024*1024" -> "1:1";
            case "1152x896", "1152*896" -> "9:7";
            case "896x1152", "896*1152" -> "7:9";
            case "1216x832", "1216*832" -> "3:2";
            case "832x1216", "832*1216" -> "2:3";
            case "1344x768", "1344*768" -> "16:9";
            case "768x1344", "768*1344" -> "9:16";
            case "1536x640", "1536*640" -> "21:9";
            case "640x1536", "640*1536" -> "9:21";
            default -> {
                // Try to parse WxH format
                String[] parts = size.split("[x*]");
                if (parts.length == 2) {
                    try {
                        int w = Integer.parseInt(parts[0].trim());
                        int h = Integer.parseInt(parts[1].trim());
                        int gcd = gcd(w, h);
                        yield (w / gcd) + ":" + (h / gcd);
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                yield null;
            }
        };
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private void postMultipartToFile(byte[] body, String boundary, Path outputFile)
            throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "image/*")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        // Stream response directly to file — never hold the full image in a String
        HttpResponse<InputStream> resp = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() / 100 != 2) {
            // Error — read small error body into string
            String errorBody;
            try (InputStream errIn = resp.body()) {
                errorBody = new String(errIn.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("HTTP " + resp.statusCode() + ": " + errorBody);
        }

        // Stream image bytes directly to file
        try (InputStream in = resp.body();
             java.io.OutputStream out = Files.newOutputStream(outputFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }
}
