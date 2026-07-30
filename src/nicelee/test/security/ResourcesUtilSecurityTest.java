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
			System.out.println("ResourcesUtilSecurityTest PASS");
		} finally {
			secret.delete();
			base.delete();
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
}
