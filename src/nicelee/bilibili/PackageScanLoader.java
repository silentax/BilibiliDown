package nicelee.bilibili;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.plugin.SourcePluginPolicy;
import nicelee.bilibili.util.ResourcesUtil;

public abstract class PackageScanLoader {

	private ClassLoader classLoader;
	private List<Class<?>> validClazzList;
	
	public static List<Class<?>> validPusherClasses;
	public static List<Class<?>> validParserClasses;
	public static List<Class<?>> validDownloaderClasses;
	public static List<java.lang.Class<?>> controllerClazzes;
	static {
		validPusherClasses = new ArrayList<Class<?>>();
		validParserClasses = new ArrayList<Class<?>>();
		validDownloaderClasses = new ArrayList<Class<?>>();
		SourcePluginPolicy.warnIfLegacyPluginDirectoriesPresent(ResourcesUtil.baseDirFile());
		// 扫描包，加载 parser 类、downloader类、pusher类
		PackageScanLoader pLoader = new PackageScanLoader() {
			@Override
			public boolean isValid(Class<?> klass) {
				Bilibili bili = klass.getAnnotation(Bilibili.class);
				if (null != bili) {
					if("parser".equals(bili.type())){
						validParserClasses.add(klass);
					}else if("downloader".equals(bili.type())){
						validDownloaderClasses.add(klass);
					}else if("pusher".equals(bili.type())){
						validPusherClasses.add(klass);
					}
				}
				return false;
			}
		};
		pLoader.scanRoot("nicelee.bilibili");
		// 按权重排序,越大越优先
		Comparator<Class<?>> comparator = new Comparator<Class<?>>() {
			@Override
			public int compare(Class<?> o1, Class<?> o2) {
				int bili1 = o1 == null? 0 : o1.getAnnotation(Bilibili.class).weight();
				int bili2 = o2 == null? 0 : o2.getAnnotation(Bilibili.class).weight();
				return bili2 - bili1;
			}
		};
		Collections.sort(validPusherClasses, comparator);
		Collections.sort(validParserClasses, comparator);
		Collections.sort(validDownloaderClasses, comparator);

		// 扫描包，加载 controller 类
		controllerClazzes = new ArrayList<java.lang.Class<?>>();
		pLoader = new PackageScanLoader() {
			@Override
			public boolean isValid(java.lang.Class<?> clazz) {
				if (clazz.getAnnotation(Controller.class) != null) {
					// System.out.println(clazz.getName());
					controllerClazzes.add(clazz);
				}
				return true;
			}
		};
		pLoader.scanRoot("nicelee.server.controller");
	}

	/**
	 * Class 类型是否符合预期，如果是，则加入列表
	 * 
	 * @param klass
	 * @return
	 */
	public abstract boolean isValid(Class<?> klass);

	public List<Class<?>> scanRoot(String packNameWithDot) {
		validClazzList = new ArrayList<Class<?>>();
		classLoader = this.getClass().getClassLoader();
		String packNameWithFileSep = packNameWithDot.replace("\\", "/").replace(".", "/");
		packNameWithDot = packNameWithDot.replace("/", ".");

		try {
			Enumeration<URL> url = classLoader.getResources(packNameWithFileSep);
			while (url.hasMoreElements()) {
				URL currentUrl = url.nextElement();
				String type = currentUrl.getProtocol();
				if (type.equals("jar")) { // jar 包
					dealWithJar(currentUrl, packNameWithFileSep);
				} else if (type.equals("file")) { // file
					File file = new File(currentUrl.toURI());
					if (file.isDirectory()) { // 目录
						dealWithFolder(packNameWithDot, file);
					} else if (file.getName().endsWith(".class")) {
						deaWithJavaClazzFile(packNameWithDot, file);
					}
				} else if (type.equals("mem")) {
					dealWithMemoryJar(currentUrl, packNameWithFileSep);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return validClazzList;
	}

	// 处理目录文件
	private void dealWithFolder(String packNameWithDot, File file) {
		if (file.exists()) {
			// file一定是目录型文件所以得到该目录下所有文件遍历它们
			File[] files = file.listFiles();
			for (File childfile : files) {
				// 如果子文件是目录，则递归处理，调用本方法递归。
				if (childfile.isDirectory()) {
					// 注意递归时候包名字要加上".文件名"后为新的包名
					// 因为后面反射时需要类名，也就是com.mec.***
					dealWithFolder(packNameWithDot + "." + childfile.getName(), childfile);
				} else {
					// 如果该文件不是目录。
					String name = childfile.getName();
					// 该文件是class类型
					if (name.contains(".class")) {
						deaWithJavaClazzFile(packNameWithDot, childfile);
					} else {
						continue;
					}
				}
			}
		} else {
			return;
		}
	}

	private void deaWithJavaClazzFile(String packNameWithDot, File file) {
		int index = file.getName().lastIndexOf(".class");
		String filename = file.getName().substring(0, index);
		Class<?> klass = null;
		try {
			klass = Class.forName(packNameWithDot + "." + filename);
			if (isValid(klass)) {
				validClazzList.add(klass);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	// 处理jar包类型
	private void dealWithJar(URL url, String packNameWithFileSep) {
		JarURLConnection jarURLConnection;
		try {
			jarURLConnection = (JarURLConnection) url.openConnection();
			JarFile jarFile = jarURLConnection.getJarFile();
			Enumeration<JarEntry> jarEntries = jarFile.entries();

			while (jarEntries.hasMoreElements()) {
				JarEntry jar = jarEntries.nextElement();
				if (jar.isDirectory() || !jar.getName().endsWith(".class") || !jar.getName().startsWith(packNameWithFileSep)) {
					continue;
				}
				// 处理class类型
				String jarName = jar.getName();
				int dotIndex = jarName.indexOf(".class");
				String className = jarName.substring(0, dotIndex).replace("/", ".");
				Class<?> klass = Class.forName(className);
				if (isValid(klass)) {
					validClazzList.add(klass);
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}
	
	// 处理自定义内存加载类型
	private void dealWithMemoryJar(URL url, String packNameWithFileSep) {
		try {
			JarInputStream jin = (JarInputStream) url.openStream();
			JarEntry entry = jin.getNextJarEntry();
			while (entry != null) {
				if (entry.isDirectory() || !entry.getName().endsWith(".class") || !entry.getName().startsWith(packNameWithFileSep)) {
					entry = jin.getNextJarEntry();
					continue;
				}
				// 处理class类型
				String jarName = entry.getName();
				int dotIndex = jarName.indexOf(".class");
				String className = jarName.substring(0, dotIndex).replace("/", ".");
				Class<?> klass = Class.forName(className, true, classLoader);
				if (isValid(klass)) {
					validClazzList.add(klass);
				}
				entry = jin.getNextJarEntry();
			}
			jin.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
	}
}
