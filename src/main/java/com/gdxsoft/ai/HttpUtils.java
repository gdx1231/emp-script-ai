package com.gdxsoft.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class.getName());

    // 缓存的 HttpClient 实例 - 线程安全，可重用
    private static volatile HttpClient cachedHttpClient;

    /**
     * 创建带有信任所有证书的 HttpClient（开发/内网环境下方便联调）。
     * <p>
     * Create an HttpClient that trusts all certificates (useful for dev/intranet).
     * 性能优化：HttpClient 实例已缓存，避免重复创建。
     *
     * @return 配置完成的 HttpClient | configured HttpClient instance
     * @throws IOException 初始化失败 | if initialization fails
     */
    public static HttpClient createHttpClient() throws IOException {
        if (cachedHttpClient != null) {
            return cachedHttpClient;
        }

        synchronized (HttpUtils.class) {
            if (cachedHttpClient != null) {
                return cachedHttpClient;
            }

            try {
                // Create a trust manager that trusts all certificates
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                } };

                // Initialize SSL context with the trust manager
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());

                // Build HttpClient with HTTP/2 support and custom SSL context
                HttpClient.Builder builder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2) // Explicitly prefer HTTP/2
                    .sslContext(sc) // Bypass SSL verification
                    .connectTimeout(Duration.ofSeconds(20));

                // 性能优化：JDK 21+ 使用虚拟线程执行器
                Executor executor = createVirtualThreadExecutorIfAvailable();
                if (executor != null) {
                    builder.executor(executor);
                    LOGGER.info("HttpClient configured with virtual thread executor (JDK 21+)");
                }

                cachedHttpClient = builder.build();
                LOGGER.info("HttpClient instance created and cached for reuse");
                return cachedHttpClient;
            } catch (Exception e) {
                LOGGER.error("Failed to configure HttpClient: " + e.getMessage(), e);
                throw new IOException("Failed to configure HttpClient: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 尝试为 HttpClient 创建虚拟线程执行器（JDK 21+）
     * 性能优化：虚拟线程可以提高 I/O 密集型任务的并发性能
     */
    private static Executor createVirtualThreadExecutorIfAvailable() {
        try {
            Class<?> executorsClass = Class.forName("java.util.concurrent.Executors");
            java.lang.reflect.Method method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
            return (Executor) method.invoke(null);
        } catch (Exception e) {
            // JDK < 21，不使用虚拟线程
            return null;
        }
    }

    /**
     * 使用 GZIP 压缩请求体字符串。
     * <p>
     * Compress the request body using GZIP.
     *
     * @param data 原始文本 | raw text data
     * @return 压缩后的字节数组 | compressed bytes
     * @throws IOException 压缩异常 | on compression errors
     */
    public static byte[] compressPostData(String data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data.getBytes("utf-8"));
            gzipOut.finish(); // Ensure all data is flushed
            return baos.toByteArray();
        }
    }

    // ============================================================
    // Multipart/form-data helpers (used by STT providers)
    // ============================================================

    /**
     * Generate a 16-byte random boundary string suitable for multipart/form-data.
     *
     * @return boundary string (no leading {@code --})
     */
    public static String newMultipartBoundary() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * One part of a multipart/form-data body.
     * <p>
     * Use {@link #text(String, String)} for form fields and {@link #file(String, byte[], String, String)}
     * for uploaded files. Both produce a part with the appropriate
     * {@code Content-Disposition} and (for files) {@code Content-Type} headers.
     */
    public static final class MultipartPart {
        private final String name;
        private final byte[] data;
        private final String filename;
        private final String contentType;

        private MultipartPart(String name, byte[] data, String filename, String contentType) {
            this.name = name;
            this.data = data;
            this.filename = filename;
            this.contentType = contentType;
        }

        /** A simple text form field (no {@code filename}, no per-part {@code Content-Type}). */
        public static MultipartPart text(String name, String value) {
            return new MultipartPart(name, value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8),
                    null, null);
        }

        /** A file upload part with the given filename and optional Content-Type. */
        public static MultipartPart file(String name, byte[] data, String filename, String contentType) {
            if (filename == null || filename.isEmpty()) {
                throw new IllegalArgumentException("filename is required for file part");
            }
            return new MultipartPart(name, data == null ? new byte[0] : data, filename, contentType);
        }

        public String getName() { return name; }
        public byte[] getData() { return data; }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
    }

    /** Pre-built multipart body with bytes and the matching {@code Content-Type} value. */
    public static final class MultipartBody {
        private final byte[] bytes;
        private final String boundary;
        private final String contentType;

        public MultipartBody(byte[] bytes, String boundary) {
            this.bytes = bytes;
            this.boundary = boundary;
            this.contentType = "multipart/form-data; boundary=" + boundary;
        }

        public byte[] bytes() { return bytes; }
        public String boundary() { return boundary; }
        public String contentType() { return contentType; }
    }

    /**
     * Build a {@code multipart/form-data} body from the given parts using a random boundary.
     *
     * @param parts parts in order
     * @return assembled body + Content-Type
     * @throws IOException on write failure
     */
    public static MultipartBody buildMultipart(List<MultipartPart> parts) throws IOException {
        return buildMultipart(parts, newMultipartBoundary());
    }

    /**
     * Build a {@code multipart/form-data} body with a caller-supplied boundary.
     * <p>
     * Package-private to support deterministic byte-level testing.
     *
     * @param parts    parts in order
     * @param boundary boundary string (no leading {@code --})
     * @return assembled body + Content-Type
     * @throws IOException on write failure
     */
    static MultipartBody buildMultipart(List<MultipartPart> parts, String boundary) throws IOException {
        if (boundary == null || boundary.isEmpty()) {
            throw new IllegalArgumentException("boundary must be non-empty");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        for (MultipartPart p : parts) {
            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            StringBuilder disp = new StringBuilder();
            disp.append("Content-Disposition: form-data; name=\"").append(p.getName()).append("\"");
            if (p.getFilename() != null) {
                disp.append("; filename=\"").append(p.getFilename()).append("\"");
            }
            out.write(disp.toString().getBytes(StandardCharsets.UTF_8));
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            if (p.getContentType() != null) {
                out.write(("Content-Type: " + p.getContentType() + crlf).getBytes(StandardCharsets.UTF_8));
            }
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            out.write(p.getData());
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return new MultipartBody(out.toByteArray(), boundary);
    }
}
