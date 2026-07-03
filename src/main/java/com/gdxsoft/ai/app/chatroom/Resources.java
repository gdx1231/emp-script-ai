package com.gdxsoft.ai.app.chatroom;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gdxsoft.easyweb.cache.CacheGroup;
import com.gdxsoft.easyweb.utils.UFileCheck;
import com.gdxsoft.easyweb.utils.UPath;

public class Resources {
	private static final Logger LOGGER = LoggerFactory.getLogger(Resources.class);
	private static final CacheGroup<String> RESOURCES = new CacheGroup<String>();
	private static final boolean IN_JAR;
	private static final String PATH;
	static final String USER_PATH;

	static {
		USER_PATH = System.getProperty("user.dir");

		String path;
		try {
			URL location = Resources.class.getProtectionDomain().getCodeSource().getLocation();
			path = location != null ? location.toURI().getPath() : USER_PATH;
		} catch (Exception e) {
			path = USER_PATH;
			LOGGER.warn("获取 classpath 失败，回退到 user.dir: {}", e.getMessage());
		}
		PATH = path;

		// 判断是否运行在 JAR 中（用类自身的真实路径，非 /aichatroom/ 下的不存在路径）
		URL self = Resources.class.getResource("Resources.class");
		IN_JAR = self != null && self.toString().contains(".jar!");
		LOGGER.info("Resources 初始化: PATH={}, IN_JAR={}", PATH, IN_JAR);
	}

	/**
	 * 读取资源内容，优先使用外部文件（支持热加载）
	 */
	public static String getResourceContent(String filePath, String charset) throws IOException {
		boolean isFromExternal = !IN_JAR;
		File externalFile = null;

		if (IN_JAR) {
			// 尝试用 JAR 外的文件覆盖
			String externalPath = UPath.getRealPath() + filePath;
			externalFile = new File(externalPath);
			if (externalFile.exists()) {
				isFromExternal = true;
			}
		}

		// 缓存命中检查
		if (isFromExternal && externalFile != null) {
			// 外部文件：检查是否已变更
			synchronized (RESOURCES) {
				if (RESOURCES.getItems().containsKey(filePath)
						&& !UFileCheck.fileChanged(externalPath(externalFile))) {
					return RESOURCES.getItem(filePath);
				}
			}
		} else if (!isFromExternal) {
			// JAR 内部文件：永不变化，直接走缓存
			synchronized (RESOURCES) {
				if (RESOURCES.getItems().containsKey(filePath)) {
					return RESOURCES.getItem(filePath);
				}
			}
		}

		// 读取
		String content;
		if (isFromExternal && externalFile != null) {
			try (InputStream is = new FileInputStream(externalFile)) {
				content = readStreamText(is, charset);
				UFileCheck.fileChanged(externalPath(externalFile)); // 记录时间戳
			}
		} else {
			URL url = Resources.class.getResource(filePath);
			if (url == null) {
				throw new IOException("资源未找到: " + filePath);
			}
			try (InputStream is = Resources.class.getResourceAsStream(filePath)) {
				if (is == null) {
					throw new IOException("资源未找到: " + filePath);
				}
				content = readStreamText(is, charset);
			}
		}

		synchronized (RESOURCES) {
			RESOURCES.addItem(filePath, content);
		}
		return content;
	}

	private static String externalPath(File f) {
		return f.getAbsolutePath();
	}

	public static String getResourceContent(String filePath) throws IOException {
		return getResourceContent(filePath, "UTF-8");
	}

	public static String readStreamText(InputStream is, String charset) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bytes = new byte[4096];
		for (int len; (len = is.read(bytes)) > 0;) {
			baos.write(bytes, 0, len);
		}
		return new String(baos.toByteArray(), charset);
	}

	/** 是否运行在 JAR 包中 */
	public static boolean isInJar() {
		return IN_JAR;
	}
}
