package nicelee.bilibili.util;

/**
 * 旧版精简 ffmpeg 二进制的固定 SHA-256 清单。
 */
public final class FFmpegBinaryManifest {

	private FFmpegBinaryManifest() {
	}

	public static String expectedSha256(String os, String arch) {
		String osArch = String.format("%s_%s", os, arch);
		switch (osArch) {
		case "linux_amd64":
			return "aa12dbb9636129f658a869b3600ec835f850a000a4d14e16411e38d37ce00d68";
		case "linux_arm64":
			return "9ac9288410fdb7ee9b31db88d07b4e2af4fff24d90fd7cfbd9a627cbbd786bd1";
		case "win_amd64":
			return "48fd5da3bf9d628c2f065fdf3f0fdc908ef86441ede1b26be9bc0e1da9a67505";
		case "win_arm64":
			return "23d66785e463a07ab548559209496008f2d29611a6fa3d4c344daa9b97024063";
		default:
			return null;
		}
	}
}
