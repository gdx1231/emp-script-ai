package com.gdxsoft.ai.modes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * &lt;tool&gt; 元素的数据模型，继承 {@link Api}。
 * <p>
 * 除 URL 调用外还支持执行本地程序：当 {@code command} 非空时按命令模板执行本地程序
 * （模板支持 @占位符，由 RequestValue 替换），否则按 Api 的 URL 方式调用。
 * <p>
 * Data model for &lt;tool&gt; element. Extends {@link Api} with local program
 * execution via a command template (@placeholders replaced from RequestValue).
 *
 */
public class Tool extends Api {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Tool.class);

	/**
	 * 本地命令执行结果
	 */
	public static class ExecResult {
		private final String commandLine;
		private final String output;
		private final int exitCode;
		private final boolean timedOut;

		ExecResult(String commandLine, String output, int exitCode, boolean timedOut) {
			this.commandLine = commandLine;
			this.output = output;
			this.exitCode = exitCode;
			this.timedOut = timedOut;
		}

		/** 实际执行的命令行（占位符已替换） */
		public String getCommandLine() {
			return commandLine;
		}

		/** 标准输出与标准错误合并后的内容 */
		public String getOutput() {
			return output;
		}

		/** 进程退出码，超时时为 -1 */
		public int getExitCode() {
			return exitCode;
		}

		/** 是否因超时强制结束 */
		public boolean isTimedOut() {
			return timedOut;
		}
	}

	/** 本地命令输出最大字符数，超出部分截断，避免撑爆 prompt */
	private static final int MAX_OUTPUT_CHARS = 100_000;

	private String command; // 本地程序命令模板，支持 @占位符

	public Tool() {
		super();
	}

	public Tool(String name, String description, String url) {
		super(name, description, url);
	}

	/**
	 * 获取本地程序命令模板
	 *
	 * @return 命令模板，未定义返回 null
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * 设置本地程序命令模板
	 *
	 * @param command 命令模板，支持 @占位符
	 */
	public void setCommand(String command) {
		this.command = command;
	}

	/**
	 * 是否为本地程序工具（command 非空）
	 *
	 * @return true 表示执行本地程序，false 表示走 URL 调用
	 */
	public boolean isLocalCommand() {
		return command != null && command.trim().length() > 0;
	}

	/**
	 * 执行本地程序命令。命令模板中的 @占位符由 rv 替换；不经 shell，
	 * 直接以 ProcessBuilder 按参数数组执行，避免 shell 注入。
	 *
	 * @param rv 请求参数容器（含 LLM 给出的 args）
	 * @return 执行结果（命令行、输出、退出码、是否超时）
	 * @throws Exception 启动进程失败时抛出
	 */
	public ExecResult executeCommand(RequestValue rv) throws Exception {
		String cmdLine = rv.replaceParameters(this.command.trim());
		List<String> argv = splitCommandLine(cmdLine);
		if (argv.isEmpty()) {
			throw new Exception("Tool command is empty: " + this.getName());
		}
		LOGGER.info("执行本地程序: {}, 命令: {}", this.getName(), cmdLine);

		ProcessBuilder pb = new ProcessBuilder(argv);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		// 异步读取输出，避免缓冲区写满导致进程阻塞
		StringBuilder out = new StringBuilder();
		Thread reader = new Thread(() -> {
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				char[] buf = new char[4096];
				int n;
				while ((n = br.read(buf)) != -1) {
					synchronized (out) {
						if (out.length() < MAX_OUTPUT_CHARS) {
							out.append(buf, 0, n);
						}
					}
				}
			} catch (IOException ignore) {
				// 进程被销毁时读流会抛出异常，忽略
			}
		});
		reader.setDaemon(true);
		reader.start();

		int timeout = this.getTimeout() > 0 ? this.getTimeout() : 5000;
		boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
		boolean timedOut = false;
		int exitCode = -1;
		if (!finished) {
			timedOut = true;
			process.destroyForcibly();
			process.waitFor(1000, TimeUnit.MILLISECONDS);
		} else {
			exitCode = process.exitValue();
		}
		reader.join(1000);

		String output;
		synchronized (out) {
			output = out.toString().trim();
		}
		if (timedOut) {
			output = (output.length() > 0 ? output + "\n" : "") + "[执行超时，已强制结束，timeout=" + timeout + "ms]";
		} else if (exitCode != 0) {
			output = (output.length() > 0 ? output + "\n" : "") + "[exit code: " + exitCode + "]";
		}
		return new ExecResult(cmdLine, output, exitCode, timedOut);
	}

	/**
	 * 按空白拆分命令行，支持单/双引号包裹含空格的参数
	 *
	 * @param cmdLine 命令行
	 * @return 参数数组
	 */
	static List<String> splitCommandLine(String cmdLine) {
		List<String> args = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inSingle = false;
		boolean inDouble = false;
		for (int i = 0; i < cmdLine.length(); i++) {
			char c = cmdLine.charAt(i);
			if (c == '\'' && !inDouble) {
				inSingle = !inSingle;
				continue;
			}
			if (c == '"' && !inSingle) {
				inDouble = !inDouble;
				continue;
			}
			if (Character.isWhitespace(c) && !inSingle && !inDouble) {
				if (cur.length() > 0) {
					args.add(cur.toString());
					cur.setLength(0);
				}
				continue;
			}
			cur.append(c);
		}
		if (cur.length() > 0) {
			args.add(cur.toString());
		}
		return args;
	}
}
