package nicelee.bilibili.util;

/**
 * 旧版应用内自更新的统一安全策略。
 *
 * <p>维护版尚未建立带 SHA-256、签名和平台安装包的可信发布链。在该链路完成前，
 * 应用只提示用户前往本仓库 Releases 页面手动获取版本，不下载或执行远端更新包。</p>
 */
public final class LegacyUpdatePolicy {

	public static final String RELEASES_URL = "https://github.com/silentax/BilibiliDown/releases";
	public static final String DISABLED_MESSAGE = "当前维护版本暂不支持安全自动更新。\n"
			+ "请前往本项目 Releases 页面手动下载，并核对发布说明：\n" + RELEASES_URL;

	private LegacyUpdatePolicy() {
	}

	public static boolean isAutomaticUpdateEnabled() {
		return false;
	}

	public static void requireAutomaticUpdateEnabled() {
		throw new UnsupportedOperationException(DISABLED_MESSAGE);
	}
}
