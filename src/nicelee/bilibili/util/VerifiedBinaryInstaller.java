package nicelee.bilibili.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 校验下载到暂存路径的二进制，并在校验成功后安装到最终路径。
 */
public final class VerifiedBinaryInstaller {

	private VerifiedBinaryInstaller() {
	}

	public static void installSha256(File stagedFile, File destination, String expectedSha256) throws IOException {
		if (stagedFile == null || !stagedFile.isFile())
			throw new IOException("待校验文件不存在");
		if (destination == null)
			throw new IOException("安装目标为空");
		if (expectedSha256 == null || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
			deleteRejected(stagedFile);
			throw new SecurityException("当前平台没有可信的 SHA-256 清单");
		}

		String actualSha256 = Encrypt.SHA256(stagedFile);
		if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
			deleteRejected(stagedFile);
			throw new SecurityException("下载文件的 SHA-256 校验失败");
		}

		File parent = destination.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
			throw new IOException("无法创建二进制安装目录");
		if (!SysUtil.isWindows() && !stagedFile.setExecutable(true, true)) {
			deleteRejected(stagedFile);
			throw new IOException("无法设置 ffmpeg 执行权限");
		}

		try {
			Files.move(stagedFile.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(stagedFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteRejected(File stagedFile) {
		try {
			Files.deleteIfExists(stagedFile.toPath());
		} catch (IOException ignored) {
			// 暂存文件不在可执行候选路径中；删除失败仍会阻止安装。
		}
	}
}
