package com.gdxsoft.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
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
}
