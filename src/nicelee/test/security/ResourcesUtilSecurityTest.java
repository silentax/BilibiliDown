package nicelee.test.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import nicelee.bilibili.util.ResourcesUtil;

/**
 * 不依赖 JUnit 的安全回归测试，便于在现有 Java 8 构建环境中单独执行。
 */
public class ResourcesUtilSecurityTest {

	public static void main(String[] args) throws Exception {
		File base = Files.createTempDirectory("bilibilidown-security-test").toFile();
		File secret = new File(base, "secret.config");
		try {
			testPathContained(base);
			testPathEscapeRejected(base);
			testSensitiveFilePermissions(secret);
			testDownloadPartCleanup(base);
			System.out.println("ResourcesUtilSecurityTest PASS");
		} finally {
			File[] files = base.listFiles();
			if (files != null) {
				for (File file : files)
					Files.deleteIfExists(file.toPath());
			}
			Files.deleteIfExists(base.toPath());
		}
	}

	private static void testPathContained(File base) throws Exception {
		File target = ResourcesUtil.resolveUnderDirectory(base, "nested/video.mp4");
		if (!target.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator))
			throw new AssertionError("合法路径没有保留在下载目录内");
	}

	private static void testPathEscapeRejected(File base) throws Exception {
		try {
			ResourcesUtil.resolveUnderDirectory(base, "../outside.mp4");
			throw new AssertionError("目录逃逸路径未被拒绝");
		} catch (IOException expected) {
			// expected
		}
	}

	private static void testSensitiveFilePermissions(File secret) throws Exception {
		ResourcesUtil.writeSensitive(secret, "test-only-secret");
		try {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(secret.toPath());
			if (permissions.contains(PosixFilePermission.GROUP_READ)
					|| permissions.contains(PosixFilePermission.GROUP_WRITE)
					|| permissions.contains(PosixFilePermission.OTHERS_READ)
					|| permissions.contains(PosixFilePermission.OTHERS_WRITE))
				throw new AssertionError("敏感文件权限未收紧");
		} catch (UnsupportedOperationException expected) {
			// 非 POSIX 平台由 File owner-only 权限回退逻辑负责。
		}
	}

	private static void testDownloadPartCleanup(File base) throws Exception {
		File destination = new File(base, "video.mp4");
		String[] removable = { "video.mp4.part", "video.mp4.part0", "video.mp4.part15", "video.mp4.part.meta",
				"video.mp4.part.meta.new", "video.mp4.part.merge" };
		for (String name : removable)
			Files.write(new File(base, name).toPath(), new byte[] { 1 });
		File unrelated = new File(base, "video.mp4.part-other");
		File otherDownload = new File(base, "other.mp4.part0");
		Files.write(destination.toPath(), new byte[] { 2 });
		Files.write(unrelated.toPath(), new byte[] { 3 });
		Files.write(otherDownload.toPath(), new byte[] { 4 });

		if (!ResourcesUtil.deleteDownloadPartFiles(destination))
			throw new AssertionError("下载临时文件清理失败");
		for (String name : removable) {
			if (new File(base, name).exists())
				throw new AssertionError("下载临时文件未删除: " + name);
		}
		if (!destination.isFile() || !unrelated.isFile() || !otherDownload.isFile())
			throw new AssertionError("临时文件清理误删了成品或无关文件");
	}
}
