package nicelee.test.security;

import nicelee.bilibili.util.Logger;

/** 验证日志出口不会泄露响应正文、签名查询参数或本机路径。 */
public class LoggerPrivacyTest {

	public static void main(String[] args) {
		testStructuredContentOmitted();
		testUrlQueryRedacted();
		testLocalPathsRedacted();
		testNormalStatusPreserved();
		testLongMessageBounded();
		System.out.println("LoggerPrivacyTest PASS");
	}

	private static void testStructuredContentOmitted() {
		String sensitiveKey = "SESS" + "DATA";
		String json = "{\"" + sensitiveKey + "\":\"test-value\",\"device\":\"fingerprint\"}";
		String sanitized = Logger.sanitizeForLog(json);
		check("<structured-content-omitted>".equals(sanitized), "JSON response must be omitted");
		check(!sanitized.contains("test-value") && !sanitized.contains("fingerprint"),
				"structured response values must not survive redaction");
	}

	private static void testUrlQueryRedacted() {
		String signedUrl = "https://media.example.test/video.m4s?deadline=123&signature=test-signature";
		String sanitized = Logger.sanitizeForLog("download " + signedUrl);
		check(sanitized.contains("https://media.example.test/video.m4s?<query-redacted>"),
				"URL host and path should remain diagnosable");
		check(!sanitized.contains("deadline=123") && !sanitized.contains("test-signature"),
				"URL query values must be removed");

		String nestedJsonQuery = "https://api.example.test/play?dm_img_list=[{\"ds\":[]}]"
				+ "&dm_cover_img_str=device-fingerprint&w_rid=signed-value&wts=123";
		String nestedSanitized = Logger.sanitizeForLog(nestedJsonQuery);
		check("https://api.example.test/play?<query-redacted>".equals(nestedSanitized),
				"quoted JSON inside a URL query must not terminate redaction early");
		check(!nestedSanitized.contains("device-fingerprint") && !nestedSanitized.contains("signed-value"),
				"device and signature fields must not survive nested-query redaction");
	}

	private static void testLocalPathsRedacted() {
		String mac = Logger.sanitizeForLog("input=/private/tmp/user-space/video.m4s");
		String windows = Logger.sanitizeForLog("input=C:\\Users\\tester\\video.m4s");
		check(!mac.contains("/private/tmp") && mac.contains("<local-path>"), "macOS path must be redacted");
		check(!windows.contains("tester") && windows.contains("<local-path>"), "Windows path must be redacted");
	}

	private static void testNormalStatusPreserved() {
		String status = "任务 BV-test 已进入合并阶段，HTTP 206";
		check(status.equals(Logger.sanitizeForLog(status)), "normal status text should remain useful");
	}

	private static void testLongMessageBounded() {
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < 1200; i++)
			value.append('x');
		String sanitized = Logger.sanitizeForLog(value.toString());
		check(sanitized.length() < value.length() && sanitized.endsWith("<truncated>"),
				"long log messages must be bounded");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
