package nicelee.bilibili.plugin;

import java.io.File;

/**
 * 外部 Java 源码和字节码插件的统一安全策略。
 */
public final class SourcePluginPolicy {

	public static final String DISABLED_MESSAGE = "外部 Java 源码/字节码插件已禁用。"
			+ "应用不会编译或执行 parsers/、pushers/ 中的文件；详见 docs/PLUGIN_SECURITY.md。";

	private SourcePluginPolicy() {
	}

	public static boolean isSourcePluginLoadingEnabled() {
		return false;
	}

	public static boolean hasLegacyPluginDirectories(File baseDirectory) {
		if (baseDirectory == null)
			return false;
		return new File(baseDirectory, "parsers").isDirectory()
				|| new File(baseDirectory, "pushers").isDirectory();
	}

	public static void warnIfLegacyPluginDirectoriesPresent(File baseDirectory) {
		if (hasLegacyPluginDirectories(baseDirectory))
			System.err.println(DISABLED_MESSAGE);
	}

	public static void requireSourcePluginLoadingEnabled() {
		throw new UnsupportedOperationException(DISABLED_MESSAGE);
	}
}
