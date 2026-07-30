package nicelee.bilibili.util;

import java.io.IOException;

/**
 * 旧版应用内自更新 API 的兼容入口。
 *
 * <p>所有会查询、下载、解压或执行更新包的方法均显式拒绝执行。未来只有在本仓库
 * 建立受控发布资产、SHA-256 校验与平台签名后，才应以新的更新组件替换本类。</p>
 */
public final class VersionManagerUtil {

	public static String downUrl;
	public static String downName;
	public static String versionTag;
	public static String versionName;
	public static String changelogs;

	private VersionManagerUtil() {
	}

	public static String getManualUpdateMessage() {
		return LegacyUpdatePolicy.DISABLED_MESSAGE;
	}

	public static String getReleasePage() {
		return LegacyUpdatePolicy.RELEASES_URL;
	}

	public static boolean queryLatestVersion() {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
		return false;
	}

	public static void downloadLatestVersion() {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
	}

	public static void unzipTargetJar(String downName) throws IOException {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
	}

	public static void trySelfUpdate(String code) {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
	}

	public static void RunCmdAndCloseApp(String code) {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
	}

	public static void main(String[] args) {
		System.err.println(getManualUpdateMessage());
	}
}
