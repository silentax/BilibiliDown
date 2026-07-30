package nicelee.bilibili.plugin;

import java.io.IOException;

/**
 * 旧外部源码插件 API 的兼容占位。
 *
 * <p>普通桌面应用无法安全隔离与主程序同进程运行的任意 Java 代码。可信插件协议完成前，
 * 本类不会编译或加载应用目录中的源码和字节码。</p>
 */
public class Plugin {

	public Plugin() {
	}

	public Plugin(String workingDir, String packageName) {
	}

	public boolean isToCompile(String clazzName) {
		return false;
	}

	public boolean compile(String clazzName) {
		SourcePluginPolicy.requireSourcePluginLoadingEnabled();
		return false;
	}

	public Class<?> loadClass(CustomClassLoader classLoader, String clazzName)
			throws IOException, ClassNotFoundException {
		SourcePluginPolicy.requireSourcePluginLoadingEnabled();
		return null;
	}
}
