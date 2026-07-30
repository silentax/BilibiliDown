package nicelee.test.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import nicelee.bilibili.PackageScanLoader;
import nicelee.bilibili.plugin.CustomClassLoader;
import nicelee.bilibili.plugin.Plugin;
import nicelee.bilibili.plugin.SourcePluginPolicy;

/**
 * 验证应用不会编译或加载外部 Java 源码和字节码插件。
 */
public class SourcePluginSecurityTest {

	public static void main(String[] args) throws Exception {
		testPolicyIsFailClosed();
		testLegacyCompilerApiIsRejected();
		testLegacyBytecodeLoaderIsRejected();
		testLegacyPluginDirectoriesAreDetectedWithoutLoading();
		testBuiltInComponentsStillLoad();
		testUnsafeImplementationIsAbsent();
		System.out.println("SourcePluginSecurityTest PASS");
	}

	private static void testPolicyIsFailClosed() {
		if (SourcePluginPolicy.isSourcePluginLoadingEnabled())
			throw new AssertionError("外部源码插件加载被启用");
	}

	private static void testLegacyCompilerApiIsRejected() throws Exception {
		final Plugin plugin = new Plugin("parsers", "nicelee.bilibili.parsers.impl");
		if (plugin.isToCompile("UntrustedParser"))
			throw new AssertionError("旧插件 API 仍请求编译外部源码");
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() {
				plugin.compile("UntrustedParser");
			}
		});
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() throws Exception {
				plugin.loadClass(new CustomClassLoader(), "UntrustedParser");
			}
		});
	}

	private static void testLegacyBytecodeLoaderIsRejected() throws Exception {
		final ExposedCustomClassLoader loader = new ExposedCustomClassLoader();
		assertDisabled(new CheckedRunnable() {
			@Override
			public void run() {
				loader.loadExternalClass("untrusted.class", "nicelee.bilibili.parsers.impl.UntrustedParser");
			}
		});
	}

	private static void testLegacyPluginDirectoriesAreDetectedWithoutLoading() throws Exception {
		Path directory = Files.createTempDirectory("bilibilidown-disabled-plugin");
		Path parserDirectory = directory.resolve("parsers");
		try {
			Files.createDirectory(parserDirectory);
			Files.write(parserDirectory.resolve("UntrustedParser.java"),
					"class UntrustedParser {}".getBytes(StandardCharsets.UTF_8));
			if (!SourcePluginPolicy.hasLegacyPluginDirectories(directory.toFile()))
				throw new AssertionError("旧插件目录未被识别并提示忽略");
			if (Files.exists(parserDirectory.resolve("UntrustedParser.class")))
				throw new AssertionError("检测旧插件目录时编译了外部源码");
		} finally {
			Files.deleteIfExists(parserDirectory.resolve("UntrustedParser.java"));
			Files.deleteIfExists(parserDirectory.resolve("UntrustedParser.class"));
			Files.deleteIfExists(parserDirectory);
			Files.deleteIfExists(directory);
		}
	}

	private static void testBuiltInComponentsStillLoad() {
		assertContainsClass(PackageScanLoader.validParserClasses, "nicelee.bilibili.parsers.impl.BVParser");
		assertContainsClass(PackageScanLoader.validDownloaderClasses,
				"nicelee.bilibili.downloaders.impl.M4SDownloader");
		assertContainsClass(PackageScanLoader.validPusherClasses,
				"nicelee.bilibili.pushers.impl.SimplePrintPush");
		assertTrustedLoaders(PackageScanLoader.validParserClasses);
		assertTrustedLoaders(PackageScanLoader.validDownloaderClasses);
		assertTrustedLoaders(PackageScanLoader.validPusherClasses);
	}

	private static void assertContainsClass(List<Class<?>> classes, String expectedName) {
		for (Class<?> clazz : classes) {
			if (expectedName.equals(clazz.getName()))
				return;
		}
		throw new AssertionError("内置组件未正常加载: " + expectedName);
	}

	private static void assertTrustedLoaders(List<Class<?>> classes) {
		for (Class<?> clazz : classes) {
			if (clazz.getClassLoader() instanceof CustomClassLoader)
				throw new AssertionError("组件由旧外部字节码加载器加载: " + clazz.getName());
		}
	}

	private static void testUnsafeImplementationIsAbsent() throws Exception {
		String scanLoader = read("src/nicelee/bilibili/PackageScanLoader.java");
		assertAbsent("PackageScanLoader.java", scanLoader, "new Plugin(", "loadTargetFolder(", "compileAndLoad(",
				"parsers.ini", "pushers.ini");
		String plugin = read("src/nicelee/bilibili/plugin/Plugin.java");
		assertAbsent("Plugin.java", plugin, "javax.tools", "ToolProvider", "JavaCompiler", "compiler.run(");
		String classLoader = read("src/nicelee/bilibili/plugin/CustomClassLoader.java");
		assertAbsent("CustomClassLoader.java", classLoader, "FileInputStream", "defineClass(");
		String policy = read("src/nicelee/bilibili/plugin/SourcePluginPolicy.java");
		assertAbsent("SourcePluginPolicy.java", policy, "System.getenv", "System.getProperty");
	}

	private static String read(String file) throws Exception {
		Path path = Paths.get(file);
		if (!Files.isRegularFile(path))
			throw new AssertionError("缺少安全基线文件: " + file);
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static void assertAbsent(String file, String content, String... forbiddenValues) {
		for (String forbidden : forbiddenValues) {
			if (content.contains(forbidden))
				throw new AssertionError(file + " 仍包含外部代码执行入口: " + forbidden);
		}
	}

	private static void assertDisabled(CheckedRunnable action) throws Exception {
		try {
			action.run();
			throw new AssertionError("旧外部插件操作未安全失败");
		} catch (UnsupportedOperationException expected) {
			if (!expected.getMessage().contains("docs/PLUGIN_SECURITY.md"))
				throw new AssertionError("禁用提示没有提供迁移说明");
		}
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class ExposedCustomClassLoader extends CustomClassLoader {
		Class<?> loadExternalClass(String classPath, String className) {
			return super.findClass(classPath, className);
		}
	}
}
