package nicelee.test.security;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import nicelee.bilibili.annotations.Config;
import nicelee.bilibili.downloaders.impl.VersionBetaDownloader;
import nicelee.bilibili.downloaders.impl.VersionDownloader;
import nicelee.bilibili.parsers.impl.VersionParser;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.bilibili.util.HttpRequestUtil;
import nicelee.bilibili.util.LegacyUpdatePolicy;
import nicelee.bilibili.util.VersionManagerUtil;
import nicelee.ui.Global;

/**
 * 验证旧应用自更新与发布脚本不能被默认启用或误执行。
 */
public class LegacyUpdateSecurityTest {

	private static final String[] LEGACY_WORKFLOWS = {
			".github/workflows/release.yml",
			".github/workflows/build-installer.yml",
			".github/workflows/upload-manually.yml",
			".github/workflows/pre-release-artifacts.yml"
	};

	private static final String[] LEGACY_SCRIPTS = {
			".github/scripts/gen_zip_sha1_for_release.sh",
			".github/scripts/gen_zip_for_pre_release.sh",
			".github/scripts/installer-win/win64_msi.py",
			".github/scripts/upload_supabase.sh",
			".github/scripts/upload_bitbucket.sh",
			".github/scripts/upload_cloudinary.sh",
			".github/scripts/upload_imagekit.sh",
			".github/scripts/upload_railway.sh",
			".github/scripts/sync_push_to_gitee.sh",
			".github/scripts/sync_push_to_bitbucket.sh",
			"src/resources/update.sh",
			"release/update.bat"
	};

	public static void main(String[] args) throws Exception {
		testPolicyIsFailClosed();
		testLegacyParsersCannotMatch();
		testLegacyManagerOperationsAreRejected();
		testUpdateSourceConfigurationRemoved();
		testLegacyConfigKeysAreDeprecated();
		testLegacyDeliveryFilesAreSafeStubs();
		System.out.println("LegacyUpdateSecurityTest PASS");
	}

	private static void testPolicyIsFailClosed() {
		if (LegacyUpdatePolicy.isAutomaticUpdateEnabled())
			throw new AssertionError("旧应用内自动更新被启用");
		if (!LegacyUpdatePolicy.RELEASES_URL.equals(VersionManagerUtil.getReleasePage()))
			throw new AssertionError("手动更新地址未指向本维护仓库");
	}

	private static void testLegacyParsersCannotMatch() {
		String legacyPackage = "BilibiliDown.v6.42.release.zip";
		if (new VersionDownloader().matches(legacyPackage))
			throw new AssertionError("正式版更新下载器仍会匹配远端更新包");
		if (new VersionBetaDownloader().matches("BilibiliDown.PreRelease"))
			throw new AssertionError("Beta 更新下载器仍会匹配远端 artifact");
		VersionParser parser = new VersionParser(new HttpRequestUtil(), null, 20);
		if (parser.matches(legacyPackage) || parser.matches("BilibiliDown.PreRelease"))
			throw new AssertionError("旧更新解析器仍会接受更新输入");
	}

	private static void testLegacyManagerOperationsAreRejected() throws Exception {
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() {
				VersionManagerUtil.queryLatestVersion();
			}
		});
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() {
				VersionManagerUtil.downloadLatestVersion();
			}
		});
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() throws Exception {
				VersionManagerUtil.unzipTargetJar("untrusted.zip");
			}
		});
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() {
				new VersionDownloader().download("https://invalid.example/update.zip", "update", 0, 0);
			}
		});
	}

	private static void testUpdateSourceConfigurationRemoved() {
		for (Field field : Global.class.getDeclaredFields()) {
			Config config = field.getAnnotation(Config.class);
			if (config != null && (config.key().startsWith("bilibili.download.update.")
					|| "bilibili.github.token".equals(config.key())))
				throw new AssertionError("仍暴露旧更新源配置: " + config.key());
		}
	}

	private static void testLegacyConfigKeysAreDeprecated() throws Exception {
		Method method = ConfigUtil.class.getDeclaredMethod("isDeprecatedConfigKey", String.class);
		method.setAccessible(true);
		assertDeprecated(method, "bilibili.github.token");
		assertDeprecated(method, "bilibili.download.update.sources");
		assertDeprecated(method, "bilibili.download.update.patterns.Github");
	}

	private static void assertDeprecated(Method method, String key) throws Exception {
		if (!Boolean.TRUE.equals(method.invoke(null, key)))
			throw new AssertionError("旧更新配置不会在保存时清理: " + key);
	}

	private static void testLegacyDeliveryFilesAreSafeStubs() throws Exception {
		for (String workflow : LEGACY_WORKFLOWS) {
			String content = read(workflow);
			assertContainsMarker(workflow, content);
			assertAbsent(workflow, content, "secrets.", "action-gh-release", "actions/checkout", "curl ", "wget ");
		}
		for (String script : LEGACY_SCRIPTS) {
			String content = read(script);
			assertContainsMarker(script, content);
			assertAbsent(script, content, "curl ", "wget ", "git push", "requests.get", "hashlib.sha1", "shell=True");
		}
	}

	private static String read(String file) throws Exception {
		Path path = Paths.get(file);
		if (!Files.isRegularFile(path))
			throw new AssertionError("缺少旧链路安全占位文件: " + file);
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static void assertContainsMarker(String file, String content) {
		if (!content.contains("LEGACY_PIPELINE_DISABLED"))
			throw new AssertionError(file + " 缺少禁用标记");
	}

	private static void assertAbsent(String file, String content, String... forbiddenValues) {
		for (String forbidden : forbiddenValues) {
			if (content.contains(forbidden))
				throw new AssertionError(file + " 仍包含可执行的旧链路内容: " + forbidden);
		}
	}

	private static void assertDisabled(CheckedRunnable action) throws Exception {
		try {
			action.run();
			throw new AssertionError("旧自动更新操作未安全失败");
		} catch (UnsupportedOperationException expected) {
			if (!expected.getMessage().contains(LegacyUpdatePolicy.RELEASES_URL))
				throw new AssertionError("禁用提示没有提供可信的手动更新地址");
		}
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
