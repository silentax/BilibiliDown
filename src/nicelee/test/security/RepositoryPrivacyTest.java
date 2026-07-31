package nicelee.test.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 防止本地运行数据、凭据和个人绝对路径重新进入 Git。 */
public class RepositoryPrivacyTest {

	private static final String SELF = "src/nicelee/test/security/RepositoryPrivacyTest.java";
	private static final long MAX_TEXT_SCAN_BYTES = 2L * 1024L * 1024L;
	private static final Set<String> FORBIDDEN_TRACKED_PATHS = new HashSet<String>(Arrays.asList(
			"config/app.config",
			"release/config/app.config",
			"config/cookies.config",
			"release/config/cookies.config",
			"config/fingerprint.config",
			"release/config/fingerprint.config",
			"release/BilibiliDown.desktop"));
	private static final Pattern LOCAL_PATH_PATTERN = Pattern.compile(
			"/Users/[A-Za-z0-9._-]+/|[A-Za-z]:\\\\Users\\\\[^\\\\/\\s]+\\\\|/home/[A-Za-z0-9._-]+/|/mnt/hgfs/");
	private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
			"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----");
	private static final Pattern BILIBILI_COOKIE_PATTERN = Pattern.compile(
			"(?:SESSDATA|bili_jct)[=:][^\\s\\\"';,]{20,}");
	private static final Pattern PROVIDER_TOKEN_PATTERN = Pattern.compile(
			"gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{10,}");
	private static final Pattern SENSITIVE_CONFIG_PATTERN = Pattern.compile(
			"^\\s*(bilibili\\.user\\.userName|bilibili\\.user\\.password|bilibili\\.download\\.push\\.token)\\s*=\\s*(.*?)\\s*$");

	public static void main(String[] args) throws Exception {
		List<String> trackedFiles = trackedFiles();
		testForbiddenTrackedFiles(trackedFiles);
		testTrackedTextFiles(trackedFiles);
		testIgnoreRules();
		testSensitivePathLoggingAbsent();
		System.out.println("RepositoryPrivacyTest PASS");
	}

	private static List<String> trackedFiles() throws Exception {
		Process process = new ProcessBuilder("git", "ls-files").redirectErrorStream(true).start();
		List<String> paths = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					paths.add(line);
				}
			}
		}
		if (process.waitFor() != 0) {
			throw new AssertionError("无法读取 Git tracked 文件列表");
		}
		return paths;
	}

	private static void testForbiddenTrackedFiles(List<String> trackedFiles) {
		for (String path : trackedFiles) {
			String lowerPath = path.toLowerCase(Locale.ROOT);
			if (FORBIDDEN_TRACKED_PATHS.contains(path) || path.startsWith("release/preview/")
					|| lowerPath.endsWith("/cookies.config") || lowerPath.endsWith("/fingerprint.config")
					|| lowerPath.equals(".env") || lowerPath.contains("/.env")
					|| lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")
					|| lowerPath.endsWith(".jks") || lowerPath.endsWith(".pem") || lowerPath.endsWith(".key")) {
				throw new AssertionError("不应跟踪本地凭据、运行配置或旧隐私截图: " + path);
			}
		}
	}

	private static void testTrackedTextFiles(List<String> trackedFiles) throws Exception {
		for (String file : trackedFiles) {
			if (SELF.equals(file)) {
				continue;
			}
			Path path = Paths.get(file);
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_TEXT_SCAN_BYTES) {
				continue;
			}
			byte[] bytes = Files.readAllBytes(path);
			if (!isText(bytes)) {
				continue;
			}
			String content = new String(bytes, StandardCharsets.UTF_8);
			assertAbsent(file, content, "个人绝对路径", LOCAL_PATH_PATTERN);
			assertAbsent(file, content, "私钥", PRIVATE_KEY_PATTERN);
			assertAbsent(file, content, "B站认证 Cookie", BILIBILI_COOKIE_PATTERN);
			assertAbsent(file, content, "第三方 provider token", PROVIDER_TOKEN_PATTERN);
			if (file.endsWith(".config")) {
				assertSensitiveConfigValuesEmpty(file, content);
			}
		}
	}

	private static boolean isText(byte[] bytes) {
		for (byte value : bytes) {
			if (value == 0) {
				return false;
			}
		}
		return true;
	}

	private static void assertAbsent(String file, String content, String type, Pattern pattern) {
		if (pattern.matcher(content).find()) {
			throw new AssertionError("tracked 文件包含" + type + ": " + file);
		}
	}

	private static void assertSensitiveConfigValuesEmpty(String file, String content) {
		String[] lines = content.split("\\r?\\n");
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index];
			if (line.trim().startsWith("#")) {
				continue;
			}
			Matcher matcher = SENSITIVE_CONFIG_PATTERN.matcher(line);
			if (matcher.matches() && !matcher.group(2).trim().isEmpty()) {
				throw new AssertionError("tracked 配置包含非空敏感值: " + file + ":" + (index + 1));
			}
		}
	}

	private static void testIgnoreRules() throws Exception {
		String ignore = new String(Files.readAllBytes(new File(".gitignore").toPath()), StandardCharsets.UTF_8);
		for (String required : Arrays.asList("*.config", "/config", "/release/config", "**/.DS_Store",
				"**/cookies.config", "**/fingerprint.config", ".env", "*.key")) {
			if (!ignore.contains(required)) {
				throw new AssertionError(".gitignore 缺少隐私保护规则: " + required);
			}
		}
	}

	private static void testSensitivePathLoggingAbsent() throws Exception {
		assertSourceAbsent("src/nicelee/ui/FrameMain.java", "System.out.println(ResourcesUtil.baseDirectory())");
		assertSourceAbsent("src/nicelee/ui/FrameMain_v3_4.java", "System.out.println(ResourcesUtil.baseDirectory())");
		assertSourceAbsent("src/nicelee/ui/Global.java", "System.out.println(\"savePath: \" + savePath)");
		assertSourceAbsent("src/nicelee/bilibili/util/ConfigUtil.java", "lockFile.getCanonicalPath()");
		assertSourceAbsent("src/nicelee/bilibili/util/net/HostSetUtil.java", "configFile.getCanonicalPath()");
	}

	private static void assertSourceAbsent(String file, String forbidden) throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
		if (source.contains(forbidden)) {
			throw new AssertionError("源码重新输出本地绝对路径: " + file);
		}
	}
}
