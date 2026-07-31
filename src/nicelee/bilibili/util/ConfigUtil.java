package nicelee.bilibili.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
//import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.ui.Global;

public class ConfigUtil {
	final static Pattern patternConfig = Pattern.compile("^[ ]*([0-9|a-z|A-Z|.|_]+)[ ]*=[ ]*([^ ]+.*$)");
	final static String DEPRECATED_INSECURE_TLS_KEY = "bilibili.https.allowInsecure";
	final static String DEPRECATED_GITHUB_TOKEN_KEY = "bilibili.github.token";
	final static String DEPRECATED_UPDATE_KEY_PREFIX = "bilibili.download.update.";
	final static String DEPRECATED_PASSWORD_KEY = "bilibili.user.password";

	/**
	 * 根据.lock文件判断，程序是否在运行
	 * 
	 * @return true/false
	 */
	public static boolean isRunning() {
		File lockFile = new File(ResourcesUtil.baseDirectory(), "config/.lock");
		try {
			System.out.println(lockFile.getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lockFile.isFile();
	}

	public static void createLock() {
		File configDir = new File(ResourcesUtil.baseDirectory(), "config");
		if (!configDir.exists())
			configDir.mkdirs();
		File lockFile = new File(configDir, ".lock");
		try {
			lockFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void deleteLock() {
		File lockFile = new File(ResourcesUtil.baseDirectory(), "config/.lock");
		lockFile.delete();
	}

	public static void initConfigs() {
		// 先初始化默认值
		try (BufferedReader buReader = new BufferedReader(
				new InputStreamReader(ConfigUtil.class.getResourceAsStream("/resources/app.config"), Charset.forName("utf-8")))) {
			String config = buReader.readLine();
			while (config != null) {
				Matcher matcher = patternConfig.matcher(config);
				if (matcher.find()) {
					Global.settings.put(matcher.group(1), matcher.group(2).trim());
				}
				config = buReader.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// 从配置文件读取写入Global.settings 
		System.out.println("----Config init begin...----");
		readConfig("config/app.config");
		readConfig("config/user.config");
		System.out.println("----Config ini end...----");
		// 根据Global.settings 初始化配置
		Global.init();
	}

	public static boolean saveConfig() {
		Map<String, String> settingsSnapshot;
		synchronized (Global.settings) {
			settingsSnapshot = new LinkedHashMap<String, String>(Global.settings);
		}
		return saveConfig(settingsSnapshot);
	}

	public static synchronized boolean saveConfig(Map<String, String> settingsSnapshot) {
		File source = ResourcesUtil.sourceOf("config/app.config");
		File tmp = new File(source.getParentFile(), "app.config.new");
		try {
			if (!tmp.exists() && !tmp.createNewFile()) {
				return false;
			}
			ResourcesUtil.restrictFileToOwner(tmp);
		} catch (IOException e) {
			return false;
		}
		try (BufferedWriter buWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "utf-8"))) {
			HashMap<String, String> copy = new LinkedHashMap<String, String>(settingsSnapshot);
			HashMap<String, String> notSaveYet = new LinkedHashMap<>(copy);
			String line;
			try (BufferedReader buReader = new BufferedReader(new InputStreamReader(new FileInputStream(source), "utf-8"))){
				line = buReader.readLine();
				while (line != null) {
					Matcher matcher = patternConfig.matcher(line);
					if (matcher.find()) {
						String key = matcher.group(1);
						if (isDeprecatedConfigKey(key)) {
							notSaveYet.remove(key);
							line = buReader.readLine();
							continue;
						}
						String value = copy.getOrDefault(key, matcher.group(2));
						line = String.format("%s = %s", key, value);
						buWriter.write(line);
						notSaveYet.remove(key);
					} else {// 原封不动写入
						buWriter.write(line);
					}
					buWriter.newLine();
					line = buReader.readLine();
				}
			}catch (IOException e) {
			}
			// 将copy 中剩下的值写入配置
			for(Entry<String, String> entry: notSaveYet.entrySet()) {
				if(!entry.getValue().isEmpty() && !Global.settingsMustCreateManualy.contains(entry.getKey())) {
					line = String.format("%s = %s", entry.getKey(), entry.getValue());
					buWriter.write(line);
					buWriter.newLine();
				}
			}
		} catch (IOException e) {
			System.err.println("保存文件失败!! ");
			return false;
		}
		if (tmp.exists()) {
			ResourcesUtil.restrictFileToOwner(tmp);
			try {
				try {
					Files.move(tmp.toPath(), source.toPath(), StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(tmp.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				ResourcesUtil.restrictFileToOwner(source);
				return true;
			} catch (IOException e) {
				System.err.println("替换配置文件失败!! ");
			}
		}
		return false;
	}

	private static void readConfig(String path) {
		File configFile = ResourcesUtil.search(path);
		if (configFile != null) {
			try (BufferedReader buReader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "utf-8"))) {
				String config = buReader.readLine();
				while (config != null) {
					Matcher matcher = patternConfig.matcher(config);
					if (matcher.find()) {
						String key = matcher.group(1);
						if (isDeprecatedConfigKey(key)) {
							System.out.printf("  ignored deprecated config key: %s%n", key);
						} else {
							Global.settings.put(key, matcher.group(2).trim());
							System.out.printf("  loaded config key: %s\r\n", key);
						}
					}
					config = buReader.readLine();
				}
			} catch (IOException e) {
				System.out.println("配置文件不存在! ");
			}
		}
	}

	private static boolean isDeprecatedConfigKey(String key) {
		return DEPRECATED_INSECURE_TLS_KEY.equals(key) || DEPRECATED_GITHUB_TOKEN_KEY.equals(key)
				|| DEPRECATED_PASSWORD_KEY.equals(key) || key.startsWith(DEPRECATED_UPDATE_KEY_PREFIX);
	}

}
