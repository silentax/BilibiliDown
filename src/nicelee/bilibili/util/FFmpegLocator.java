package nicelee.bilibili.util;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 跨平台查找并验证 ffmpeg。候选命令通过 ProcessBuilder 直接执行，不经过 shell。
 */
public final class FFmpegLocator {

	private static final long PROBE_TIMEOUT_SECONDS = 3L;
	private static final File NULL_FILE = new File(SysUtil.isWindows() ? "NUL" : "/dev/null");

	private FFmpegLocator() {
	}

	public static String locate(String configuredPath, File applicationDirectory) {
		for (String candidate : candidates(configuredPath, applicationDirectory)) {
			if (isAvailable(candidate))
				return canonicalExecutable(candidate);
		}
		return null;
	}

	public static boolean isAvailable(String executable) {
		if (executable == null || executable.trim().isEmpty())
			return false;
		Process process = null;
		try {
			ProcessBuilder builder = new ProcessBuilder(executable, "-version");
			builder.redirectOutput(Redirect.to(NULL_FILE));
			builder.redirectError(Redirect.to(NULL_FILE));
			process = builder.start();
			if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(1L, TimeUnit.SECONDS);
				return false;
			}
			return process.exitValue() == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (process != null)
				process.destroyForcibly();
			return false;
		} catch (IOException | SecurityException e) {
			return false;
		}
	}

	private static Set<String> candidates(String configuredPath, File applicationDirectory) {
		Set<String> candidates = new LinkedHashSet<>();
		String executableName = "ffmpeg" + SysUtil.getEXE_SUFFIX();
		String configured = expandHome(configuredPath);

		if (configured != null && !configured.trim().isEmpty()) {
			File configuredFile = new File(configured);
			if (configuredFile.isAbsolute())
				add(candidates, configuredFile.getPath());
			else if (applicationDirectory != null)
				add(candidates, new File(applicationDirectory, configured).getPath());
			add(candidates, configured);
		}

		if (applicationDirectory != null)
			add(candidates, new File(applicationDirectory, executableName).getPath());
		add(candidates, executableName);

		if (SysUtil.isMac()) {
			add(candidates, "/opt/homebrew/bin/ffmpeg");
			add(candidates, "/usr/local/bin/ffmpeg");
			add(candidates, "/opt/local/bin/ffmpeg");
		} else if (SysUtil.isWindows()) {
			addFromEnvironment(candidates, "LOCALAPPDATA", "Microsoft/WinGet/Links/ffmpeg.exe");
			addFromEnvironment(candidates, "ProgramData", "chocolatey/bin/ffmpeg.exe");
		}
		return candidates;
	}

	private static void addFromEnvironment(Set<String> candidates, String variable, String relativePath) {
		String root = System.getenv(variable);
		if (root != null && !root.trim().isEmpty())
			add(candidates, new File(root, relativePath).getPath());
	}

	private static void add(Set<String> candidates, String candidate) {
		if (candidate != null && !candidate.trim().isEmpty())
			candidates.add(candidate.trim());
	}

	private static String expandHome(String path) {
		if (path == null)
			return null;
		if ("~".equals(path))
			return System.getProperty("user.home", path);
		if (path.startsWith("~/") || path.startsWith("~\\"))
			return new File(System.getProperty("user.home", ""), path.substring(2)).getPath();
		return path;
	}

	private static String canonicalExecutable(String candidate) {
		File file = new File(candidate);
		if (!file.isAbsolute() && !file.exists())
			return candidate;
		try {
			return file.getCanonicalPath();
		} catch (IOException e) {
			return file.getAbsolutePath();
		}
	}
}
