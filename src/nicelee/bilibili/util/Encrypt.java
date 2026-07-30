package nicelee.bilibili.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Encrypt {

	
	public static String MD5(String param) {
		byte[] secretBytes = null;
		try {
			// 生成一个MD5加密计算摘要
			MessageDigest md = MessageDigest.getInstance("MD5");
			// 对字符串进行加密
			md.update(param.getBytes());
			// 获得加密后的数据
			secretBytes = md.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("没有md5这个算法！");
		}
		String md5code = new BigInteger(1,secretBytes).toString(16);// 16进制数字
		for (int i = 0; i < 32 - md5code.length(); i++) {
			md5code = "0" + md5code;
		}
		return md5code;
	}
	
	public static String SHA1(File f) {
		return digest(f, "SHA1");
	}

	public static String SHA256(File f) {
		return digest(f, "SHA-256");
	}

	private static String digest(File f, String algorithm) {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			byte[] buffer = new byte[8192];
			try (FileInputStream in = new FileInputStream(f)) {
				int len = in.read(buffer);
				while (len > 0) {
					md.update(buffer, 0, len);
					len = in.read(buffer);
				}
			}
			byte[] digest = md.digest();

			StringBuilder hexstr = new StringBuilder(digest.length * 2);
			for (int i = 0; i < digest.length; i++) {
				String shaHex = Integer.toHexString(digest[i] & 0xFF);
				if (shaHex.length() < 2) {
					hexstr.append(0);
				}
				hexstr.append(shaHex);
			}
			return hexstr.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("不支持摘要算法: " + algorithm, e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
