package nicelee.bilibili.util;

import java.util.regex.Pattern;

public class Logger {

	private static final int MAX_LOG_LENGTH = 1000;
	private static final Pattern URL_WITH_QUERY = Pattern.compile(
			"(?i)(https?://[^\\s\\\"'<>?#]+)[?]\\S*");
	private static final Pattern LOCAL_PATH = Pattern.compile(
			"(?:(?:/Users|/home|/private|/var/folders)/[^\\s,\\]\\)]+|[A-Za-z]:\\\\Users\\\\[^\\s,\\]\\)]+)");

	final static boolean mute;
	static {
		mute = !"true".equals(System.getProperty("bilibili.prop.log", "true"));
	}

	public static void print(Object str) {
		if (mute)
			return;
		System.out.print(sanitizeForLog(str));
	}

	public static void println() {
		if (mute)
			return;
		System.out.println();
	}

	public static void printf(String str, Object... obj) {
		if (mute)
			return;
		StackTraceElement ele = Thread.currentThread().getStackTrace()[2];
		String file = ele.getFileName();
		file = file.substring(0, file.length() - 5);
		String method = ele.getMethodName();
		int line = ele.getLineNumber();
		String preStr = sanitizeForLog(String.format(str, obj));
		String result = String.format("%s-%s/%d : %s", file, method, line, preStr);
		System.out.println(result);
	}

	public static void println(String str) {
		if (mute)
			return;
		StackTraceElement ele = Thread.currentThread().getStackTrace()[2];
		String file = ele.getFileName();
		file = file.substring(0, file.length() - 5);
		String method = ele.getMethodName();
		int line = ele.getLineNumber();
		String result = String.format("%s-%s/%d : %s", file, method, line, sanitizeForLog(str));
		System.out.println(result);
	}

	public static void println(Object obj) {
		if (mute)
			return;
		StackTraceElement ele = Thread.currentThread().getStackTrace()[2];
		String file = ele.getFileName();
		file = file.substring(0, file.length() - 5);
		String method = ele.getMethodName();
		int line = ele.getLineNumber();
		String result = String.format("%s-%s/%d : %s", file, method, line, sanitizeForLog(obj));
		System.out.println(result);
	}

	/**
	 * 日志出口的最后一道隐私保护。调用方可以记录阶段和状态，但不能把完整 API
	 * 响应、签名查询参数或本机绝对路径写入用户可能分享的控制台日志。
	 */
	public static String sanitizeForLog(Object value) {
		String text = String.valueOf(value);
		String trimmed = text.trim();
		if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
				|| (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
			return "<structured-content-omitted>";
		}
		String sanitized = URL_WITH_QUERY.matcher(text).replaceAll("$1?<query-redacted>");
		sanitized = LOCAL_PATH.matcher(sanitized).replaceAll("<local-path>");
		if (sanitized.length() > MAX_LOG_LENGTH)
			return sanitized.substring(0, MAX_LOG_LENGTH) + "<truncated>";
		return sanitized;
	}
}
