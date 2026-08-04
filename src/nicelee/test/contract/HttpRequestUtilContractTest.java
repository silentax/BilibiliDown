package nicelee.test.contract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import nicelee.bilibili.exceptions.Status412Exception;
import nicelee.bilibili.util.HttpRequestUtil;

/**
 * 使用 loopback Mock HTTP 服务验证基础请求、断点续传和下载路径边界。
 * 测试不访问 B站或其它外部网络。
 */
public class HttpRequestUtilContractTest {

	private static final byte[] DOWNLOAD_PAYLOAD = "0123456789-contract-payload".getBytes(StandardCharsets.UTF_8);

	public static void main(String[] args) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/get", new FixedResponseHandler(200, "contract-ok".getBytes(StandardCharsets.UTF_8)));
		server.createContext("/post", new EchoHandler());
		server.createContext("/status-412", new FixedResponseHandler(412, new byte[0]));
		server.createContext("/download", new RangeResponseHandler(true));
		server.createContext("/ignore-range", new RangeResponseHandler(false));
		server.createContext("/truncated", new TruncatedResponseHandler());
		server.start();

		File downloadDirectory = Files.createTempDirectory("bilibilidown-http-contract").toFile();
		try {
			String baseUrl = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
					+ server.getAddress().getPort();
			testGetAndPost(baseUrl);
			testStatus412(baseUrl);
			testFreshDownload(baseUrl, downloadDirectory);
			testResumeDownload(baseUrl, downloadDirectory);
			testIgnoredRangeRestartsSafely(baseUrl, downloadDirectory);
			testTruncatedDownloadRejected(baseUrl, downloadDirectory);
			testPathEscapeRejected(baseUrl, downloadDirectory);
			System.out.println("HTTP contract tests passed");
		} finally {
			server.stop(0);
			deleteKnownTestFiles(downloadDirectory);
		}
	}

	private static void testGetAndPost(String baseUrl) {
		HttpRequestUtil util = new HttpRequestUtil();
		String getResult = util.getContent(baseUrl + "/get", new HashMap<String, String>());
		check("contract-ok".equals(getResult), "GET response body mismatch");

		String postResult = util.postContent(baseUrl + "/post", new HashMap<String, String>(), "post-contract");
		check("post-contract".equals(postResult), "POST response body mismatch");
	}

	private static void testStatus412(String baseUrl) {
		HttpRequestUtil util = new HttpRequestUtil();
		try {
			util.getContent(baseUrl + "/status-412", new HashMap<String, String>());
			throw new AssertionError("HTTP 412 must remain visible to the caller");
		} catch (Status412Exception expected) {
			// expected
		}
	}

	private static void testFreshDownload(String baseUrl, File directory) throws Exception {
		HttpRequestUtil util = downloadUtil(directory);
		check(util.download(baseUrl + "/download", "fresh.bin", new HashMap<String, String>()),
				"fresh download failed");
		assertFileContent(new File(directory, "fresh.bin"));
	}

	private static void testResumeDownload(String baseUrl, File directory) throws Exception {
		File partial = new File(directory, "resume.bin.part");
		Files.write(partial.toPath(), Arrays.copyOf(DOWNLOAD_PAYLOAD, 7));
		HttpRequestUtil util = downloadUtil(directory);
		check(util.download(baseUrl + "/download", "resume.bin", new HashMap<String, String>()),
				"range resume download failed");
		assertFileContent(new File(directory, "resume.bin"));
	}

	private static void testIgnoredRangeRestartsSafely(String baseUrl, File directory) throws Exception {
		File partial = new File(directory, "ignore-range.bin.part");
		Files.write(partial.toPath(), Arrays.copyOf(DOWNLOAD_PAYLOAD, 5));
		HttpRequestUtil util = downloadUtil(directory);
		check(util.download(baseUrl + "/ignore-range", "ignore-range.bin", new HashMap<String, String>()),
				"download must restart when a server ignores Range");
		assertFileContent(new File(directory, "ignore-range.bin"));
	}

	private static void testTruncatedDownloadRejected(String baseUrl, File directory) {
		HttpRequestUtil util = downloadUtil(directory);
		check(!util.download(baseUrl + "/truncated", "truncated.bin", new HashMap<String, String>()),
				"truncated response must not be published as a completed file");
		check(!new File(directory, "truncated.bin").exists(), "truncated final file must not exist");
		check(new File(directory, "truncated.bin.part").exists(), "truncated partial file must remain resumable");
	}

	private static void testPathEscapeRejected(String baseUrl, File directory) {
		HttpRequestUtil util = downloadUtil(directory);
		try {
			util.download(baseUrl + "/download", "../outside.bin", new HashMap<String, String>());
			throw new AssertionError("download path escape must be rejected");
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	private static HttpRequestUtil downloadUtil(File directory) {
		HttpRequestUtil util = new HttpRequestUtil();
		util.setSavePath(directory.getAbsolutePath());
		return util;
	}

	private static void assertFileContent(File file) throws Exception {
		check(file.isFile(), "completed download is missing: " + file.getName());
		check(Arrays.equals(DOWNLOAD_PAYLOAD, Files.readAllBytes(file.toPath())),
				"completed download content mismatch: " + file.getName());
	}

	private static void deleteKnownTestFiles(File directory) throws IOException {
		for (String name : Arrays.asList("fresh.bin", "fresh.bin.part", "resume.bin", "resume.bin.part",
				"ignore-range.bin", "ignore-range.bin.part", "truncated.bin", "truncated.bin.part")) {
			Files.deleteIfExists(new File(directory, name).toPath());
		}
		Files.deleteIfExists(directory.toPath());
	}

	private static byte[] readAll(InputStream input) throws IOException {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int read;
			while ((read = input.read(buffer)) != -1) {
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		} finally {
			input.close();
		}
	}

	private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
		exchange.sendResponseHeaders(status, body.length);
		try {
			exchange.getResponseBody().write(body);
		} finally {
			exchange.close();
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static class FixedResponseHandler implements HttpHandler {
		private final int status;
		private final byte[] body;

		FixedResponseHandler(int status, byte[] body) {
			this.status = status;
			this.body = body;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			send(exchange, status, body);
		}
	}

	private static class EchoHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			send(exchange, 200, readAll(exchange.getRequestBody()));
		}
	}

	private static class RangeResponseHandler implements HttpHandler {
		private final boolean honorRange;

		RangeResponseHandler(boolean honorRange) {
			this.honorRange = honorRange;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String range = exchange.getRequestHeaders().getFirst("Range");
			if (honorRange && range != null && range.matches("bytes=[0-9]+-")) {
				int offset = Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
				byte[] remaining = Arrays.copyOfRange(DOWNLOAD_PAYLOAD, offset, DOWNLOAD_PAYLOAD.length);
				exchange.getResponseHeaders().set("Content-Range",
						"bytes " + offset + "-" + (DOWNLOAD_PAYLOAD.length - 1) + "/" + DOWNLOAD_PAYLOAD.length);
				send(exchange, 206, remaining);
				return;
			}
			send(exchange, 200, DOWNLOAD_PAYLOAD);
		}
	}

	private static class TruncatedResponseHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			exchange.sendResponseHeaders(200, DOWNLOAD_PAYLOAD.length + 10);
			try {
				exchange.getResponseBody().write(DOWNLOAD_PAYLOAD);
			} finally {
				exchange.close();
			}
		}
	}
}
