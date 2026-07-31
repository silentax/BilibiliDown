package nicelee.test.security;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import nicelee.bilibili.annotations.Config;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.server.controller.ControllerLogin;
import nicelee.server.core.SocketServer;
import nicelee.ui.DialogLogin;
import nicelee.ui.DialogSMSLogin;
import nicelee.ui.Global;

/** 验证登录凭据不会回退到普通配置、全局字符串或固定像素旧窗口。 */
public class CredentialSecurityTest {

	public static void main(String[] args) throws Exception {
		testPasswordConfigIsDeprecated();
		testGlobalPasswordFieldRemoved();
		testLoginDialogsAvoidLegacyCredentialPatterns();
		testClosedLoginCallbacksReturnErrors();
		testServerCanStopBeforeBinding();
		System.out.println("CredentialSecurityTest PASS");
	}

	private static void testPasswordConfigIsDeprecated() throws Exception {
		Method method = ConfigUtil.class.getDeclaredMethod("isDeprecatedConfigKey", String.class);
		method.setAccessible(true);
		if (!Boolean.TRUE.equals(method.invoke(null, "bilibili.user.password")))
			throw new AssertionError("旧明文密码配置不会在读取和保存时清理");
	}

	private static void testGlobalPasswordFieldRemoved() {
		for (Field field : Global.class.getDeclaredFields()) {
			Config config = field.getAnnotation(Config.class);
			if (config != null && "bilibili.user.password".equals(config.key()))
				throw new AssertionError("Global 仍暴露明文密码配置");
			if ("password".equals(field.getName()))
				throw new AssertionError("Global 仍长期持有密码字符串");
		}
	}

	private static void testLoginDialogsAvoidLegacyCredentialPatterns() throws Exception {
		String passwordDialog = read("src/nicelee/ui/DialogLogin.java");
		assertAbsent(passwordDialog, "Global.password", "new JPasswordField(\"123456\")", "setLayout(null)",
				"setBounds(");
		assertContains(passwordDialog, "char[] pendingPassword", "public synchronized String login", "Thread-PasswordCaptcha", "SwingDispatch",
				"catch (RuntimeException error)", "if (closing)");

		String smsDialog = read("src/nicelee/ui/DialogSMSLogin.java");
		assertAbsent(smsDialog, "setLayout(null)", "setBounds(");
		assertContains(smsDialog, "public synchronized String sendSMS", "Thread-SmsCaptcha", "Thread-SmsLogin", "SwingDispatch",
				"catch (RuntimeException error)", "finally", "if (closing)");
	}

	private static void testClosedLoginCallbacksReturnErrors() throws Exception {
		DialogLogin.Instance = null;
		DialogSMSLogin.Instance = null;
		ControllerLogin controller = new ControllerLogin();
		String params = "token=t&challenge=c&validate=v&seccode=s";

		StringWriter passwordResponse = new StringWriter();
		controller.login(new BufferedWriter(passwordResponse), new ByteArrayOutputStream(), params);
		assertClosedWindowResponse(passwordResponse.toString(), "密码登录");

		StringWriter smsResponse = new StringWriter();
		controller.sms(new BufferedWriter(smsResponse), new ByteArrayOutputStream(), params);
		assertClosedWindowResponse(smsResponse.toString(), "短信登录");
	}

	private static void assertClosedWindowResponse(String response, String flow) {
		if (!response.contains("\"code\":777") || !response.contains("登录窗口已关闭"))
			throw new AssertionError(flow + "窗口关闭后仍返回成功: " + response);
	}

	private static void testServerCanStopBeforeBinding() {
		new SocketServer(0, 1).stopServer();
	}

	private static String read(String file) throws Exception {
		Path path = Paths.get(file);
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static void assertAbsent(String content, String... forbiddenValues) {
		for (String forbidden : forbiddenValues) {
			if (content.contains(forbidden))
				throw new AssertionError("登录实现重新出现不安全模式: " + forbidden);
		}
	}

	private static void assertContains(String content, String... requiredValues) {
		for (String required : requiredValues) {
			if (!content.contains(required))
				throw new AssertionError("登录实现缺少安全边界: " + required);
		}
	}
}
