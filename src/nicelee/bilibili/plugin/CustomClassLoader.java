package nicelee.bilibili.plugin;

/**
 * 旧外部字节码加载器的兼容占位。
 */
public class CustomClassLoader extends ClassLoader {

	public CustomClassLoader() {
		super(CustomClassLoader.class.getClassLoader());
	}

	protected Class<?> findClass(String classPath, String className) {
		SourcePluginPolicy.requireSourcePluginLoadingEnabled();
		return null;
	}

	@Override
	protected Class<?> findClass(String className) throws ClassNotFoundException {
		throw new ClassNotFoundException(SourcePluginPolicy.DISABLED_MESSAGE);
	}
}
