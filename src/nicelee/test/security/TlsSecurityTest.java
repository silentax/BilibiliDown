package nicelee.test.security;

import java.lang.reflect.Field;
import java.util.Properties;

import nicelee.bilibili.annotations.Config;
import nicelee.bilibili.pushers.impl.MailPush;
import nicelee.ui.Global;

/**
 * 验证应用不再暴露跳过 TLS 证书校验的配置或 SMTP socket factory。
 */
public class TlsSecurityTest {

	public static void main(String[] args) {
		testInsecureTlsConfigRemoved();
		testTrustAllUtilityRemoved();
		testQqMailUsesDefaultTrustStore();
		System.out.println("TlsSecurityTest PASS");
	}

	private static void testInsecureTlsConfigRemoved() {
		for (Field field : Global.class.getDeclaredFields()) {
			Config config = field.getAnnotation(Config.class);
			if (config != null && "bilibili.https.allowInsecure".equals(config.key()))
				throw new AssertionError("仍然暴露跳过 TLS 证书校验的配置");
		}
	}

	private static void testTrustAllUtilityRemoved() {
		try {
			Class.forName("nicelee.bilibili.util.net.TrustAllCertSSLUtil");
			throw new AssertionError("Trust-All TLS 工具类仍可被加载");
		} catch (ClassNotFoundException expected) {
			// 安全基线：应用中不再提供忽略证书链校验的工具类。
		}
	}

	private static void testQqMailUsesDefaultTrustStore() {
		Properties properties = MailPush.getProps("security-test@qq.com");
		if (properties.containsKey("mail.smtp.ssl.socketFactory"))
			throw new AssertionError("QQ SMTP 仍然使用自定义 SSL socket factory");
		if (!"true".equals(properties.getProperty("mail.smtp.ssl.enable")))
			throw new AssertionError("QQ SMTP 未启用标准 TLS");
		if (!"true".equals(properties.getProperty("mail.smtp.ssl.checkserveridentity")))
			throw new AssertionError("QQ SMTP 未启用服务端主机名校验");
	}
}
