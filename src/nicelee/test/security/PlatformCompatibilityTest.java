package nicelee.test.security;

import java.io.File;
import java.nio.file.Files;

import nicelee.bilibili.util.FFmpegLocator;
import nicelee.bilibili.util.SysUtil;

/**
 * Windows 与 macOS 共用的运行环境探测回归测试。
 */
public class PlatformCompatibilityTest {

	public static void main(String[] args) throws Exception {
		testOperatingSystemDetection();
		testConfiguredExecutableTakesPrecedence();
		testMissingExecutableRejected();
		System.out.println("PlatformCompatibilityTest PASS");
	}

	private static void testOperatingSystemDetection() {
		String rawOS = System.getProperty("os.name", "").toLowerCase();
		if (rawOS.startsWith("win") && !SysUtil.isWindows())
			throw new AssertionError("Windows 未被正确识别");
		if ((rawOS.startsWith("mac") || rawOS.startsWith("darwin")) && !SysUtil.isMac())
			throw new AssertionError("macOS 未被正确识别");
	}

	private static void testConfiguredExecutableTakesPrecedence() throws Exception {
		File applicationDirectory = Files.createTempDirectory("bilibilidown-platform-test").toFile();
		try {
			File javaExecutable = new File(new File(System.getProperty("java.home"), "bin"),
					"java" + SysUtil.getEXE_SUFFIX());
			String located = FFmpegLocator.locate(javaExecutable.getAbsolutePath(), applicationDirectory);
			if (located == null || !new File(located).getCanonicalFile().equals(javaExecutable.getCanonicalFile()))
				throw new AssertionError("带绝对路径的可执行文件未被优先识别");
		} finally {
			applicationDirectory.delete();
		}
	}

	private static void testMissingExecutableRejected() throws Exception {
		File missing = new File(Files.createTempDirectory("bilibilidown-missing-executable").toFile(),
				"missing-ffmpeg-" + System.nanoTime());
		try {
			if (FFmpegLocator.isAvailable(missing.getAbsolutePath()))
				throw new AssertionError("不存在的 ffmpeg 被误判为可用");
		} finally {
			missing.getParentFile().delete();
		}
	}
}
