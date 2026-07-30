package nicelee.test.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import nicelee.bilibili.util.Encrypt;
import nicelee.bilibili.util.FFmpegBinaryManifest;
import nicelee.bilibili.util.VerifiedBinaryInstaller;

/**
 * ffmpeg 下载清单、SHA-256 校验和暂存安装的安全回归测试。
 */
public class FFmpegDownloadSecurityTest {

	public static void main(String[] args) throws Exception {
		testManifestCoverage();
		testDigestKnownVectors();
		testVerifiedFileInstalled();
		testMismatchedFileRejectedAndDeleted();
		System.out.println("FFmpegDownloadSecurityTest PASS");
	}

	private static void testManifestCoverage() {
		assertDigest("win", "amd64");
		assertDigest("win", "arm64");
		assertDigest("linux", "amd64");
		assertDigest("linux", "arm64");
		if (FFmpegBinaryManifest.expectedSha256("mac", "arm64") != null)
			throw new AssertionError("没有可信清单的 macOS 平台不应启用自动下载");
	}

	private static void assertDigest(String os, String arch) {
		String digest = FFmpegBinaryManifest.expectedSha256(os, arch);
		if (digest == null || !digest.matches("[0-9a-f]{64}"))
			throw new AssertionError(os + "_" + arch + " 的 SHA-256 清单无效");
	}

	private static void testDigestKnownVectors() throws Exception {
		File directory = Files.createTempDirectory("bilibilidown-sha256-vector").toFile();
		File payload = new File(directory, "payload");
		try {
			Files.write(payload.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
			String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
			if (!expected.equals(Encrypt.SHA256(payload)))
				throw new AssertionError("SHA-256 标准向量计算错误");
			String legacySha1 = "a9993e364706816aba3e25717850c26c9cd0d89d";
			if (!legacySha1.equals(Encrypt.SHA1(payload)))
				throw new AssertionError("SHA-1 兼容计算发生回归");
		} finally {
			Files.deleteIfExists(payload.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	private static void testVerifiedFileInstalled() throws Exception {
		File directory = Files.createTempDirectory("bilibilidown-verified-binary").toFile();
		File staged = new File(directory, "ffmpeg.download");
		File destination = new File(directory, "ffmpeg");
		try {
			Files.write(staged.toPath(), "verified-test-payload".getBytes(StandardCharsets.UTF_8));
			VerifiedBinaryInstaller.installSha256(staged, destination, Encrypt.SHA256(staged));
			if (!destination.isFile() || staged.exists())
				throw new AssertionError("校验通过的文件未从暂存路径安装到最终路径");
		} finally {
			Files.deleteIfExists(staged.toPath());
			Files.deleteIfExists(destination.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	private static void testMismatchedFileRejectedAndDeleted() throws Exception {
		File directory = Files.createTempDirectory("bilibilidown-rejected-binary").toFile();
		File staged = new File(directory, "ffmpeg.download");
		File destination = new File(directory, "ffmpeg");
		try {
			Files.write(staged.toPath(), "untrusted-test-payload".getBytes(StandardCharsets.UTF_8));
			try {
				VerifiedBinaryInstaller.installSha256(staged, destination,
						"0000000000000000000000000000000000000000000000000000000000000000");
				throw new AssertionError("SHA-256 不匹配的文件被安装");
			} catch (SecurityException expected) {
				if (staged.exists() || destination.exists())
					throw new AssertionError("被拒绝的文件仍留在暂存或最终路径");
			}
		} finally {
			Files.deleteIfExists(staged.toPath());
			Files.deleteIfExists(destination.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}
}
