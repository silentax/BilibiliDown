package nicelee.test.contract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import nicelee.bilibili.enums.StatusEnum;
import nicelee.bilibili.util.HttpRequestUtilEx;
import nicelee.ui.Global;

/** 使用 loopback HTTP 服务验证多线程分片、续传、取消和失败边界。 */
public class HttpRequestUtilExContractTest {

	private static final Pattern REQUEST_RANGE = Pattern.compile("^bytes=([0-9]+)-([0-9]+)$");
	private static final byte[] PAYLOAD = createPayload(96 * 1024 + 7);

	public static void main(String[] args) throws Exception {
		ExecutorService serverExecutor = Executors.newCachedThreadPool();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		RangeHandler normal = new RangeHandler(Mode.NORMAL);
		RangeHandler ignored = new RangeHandler(Mode.IGNORE_RANGE);
		RangeHandler badContentRange = new RangeHandler(Mode.BAD_CONTENT_RANGE);
		RangeHandler truncated = new RangeHandler(Mode.TRUNCATED);
		RangeHandler status412 = new RangeHandler(Mode.STATUS_412);
		RangeHandler status416 = new RangeHandler(Mode.STATUS_416);
		RangeHandler slow = new RangeHandler(Mode.NORMAL);
		slow.slow = true;
		server.createContext("/normal", normal);
		server.createContext("/ignore-range", ignored);
		server.createContext("/bad-content-range", badContentRange);
		server.createContext("/truncated", truncated);
		server.createContext("/status-412", status412);
		server.createContext("/status-416", status416);
		server.createContext("/slow", slow);
		server.setExecutor(serverExecutor);
		server.start();

		File directory = Files.createTempDirectory("bilibilidown-http-multithread-contract").toFile();
		int previousThreadCount = Global.multiThreadCnt;
		long previousThreshold = Global.multiThreadMinFileSize;
		Pattern previousSingleThreadPattern = Global.singleThreadPattern;
		try {
			Global.multiThreadCnt = 3;
			Global.multiThreadMinFileSize = 0;
			Global.singleThreadPattern = Pattern.compile("a^");
			String baseUrl = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
					+ server.getAddress().getPort();
			testRangeProbe(baseUrl, directory);
			testFreshMultiThreadDownload(baseUrl, directory, normal);
			testPartResume(baseUrl, directory);
			testLayoutChangeInvalidatesParts(baseUrl, directory);
			testIgnoredRangeFallsBack(baseUrl, directory);
			testInvalidContentRangeRejected(baseUrl, directory);
			testTruncatedPartRejected(baseUrl, directory);
			testNonSuccessStatusesRejected(baseUrl, directory);
			testCancelAndResume(baseUrl, directory, slow);
			testThreadCountBounded(baseUrl, directory, normal);
			testPathEscapeRejected(baseUrl, directory);
			System.out.println("HTTP multi-thread contract tests passed");
		} finally {
			Global.multiThreadCnt = previousThreadCount;
			Global.multiThreadMinFileSize = previousThreshold;
			Global.singleThreadPattern = previousSingleThreadPattern;
			server.stop(0);
			serverExecutor.shutdownNow();
			deleteTestDirectory(directory);
		}
	}

	private static void testRangeProbe(String baseUrl, File directory) {
		HttpRequestUtilEx util = downloadUtil(directory);
		check(util.getTotalSize(baseUrl + "/normal", new HashMap<String, String>()) == PAYLOAD.length,
				"range probe must return the Content-Range total");
		check(util.getTotalSize(baseUrl + "/ignore-range", new HashMap<String, String>()) == 0,
				"ignored Range must be reported as unsupported");
	}

	private static void testFreshMultiThreadDownload(String baseUrl, File directory, RangeHandler handler)
			throws Exception {
		int requestsBefore = handler.requests.get();
		HttpRequestUtilEx util = downloadUtil(directory);
		check(util.download(baseUrl + "/normal", "multi-fresh.bin", new HashMap<String, String>()),
				"fresh multi-thread download failed");
		assertCompletedFile(directory, "multi-fresh.bin");
		check(util.getDownloadedFileSize() == PAYLOAD.length, "downloaded byte count must equal final size");
		check(util.getTotalFileSize() == PAYLOAD.length, "total byte count must equal payload size");
		check(handler.requests.get() - requestsBefore == 4, "three workers plus one probe were expected");
		assertNoPartFiles(directory, "multi-fresh.bin");
	}

	private static void testPartResume(String baseUrl, File directory) throws Exception {
		File partZero = new File(directory, "multi-resume.bin.part0");
		Files.write(partZero.toPath(), Arrays.copyOfRange(PAYLOAD, 0, 2048));
		writePartMetadata(directory, "multi-resume.bin", 3);
		HttpRequestUtilEx util = downloadUtil(directory);
		check(util.download(baseUrl + "/normal", "multi-resume.bin", new HashMap<String, String>()),
				"multi-thread part resume failed");
		assertCompletedFile(directory, "multi-resume.bin");
		check(RangeHandler.sawKnownResumeRange.getAndSet(false), "existing part bytes were not resumed");
		assertNoPartFiles(directory, "multi-resume.bin");
	}

	private static void testLayoutChangeInvalidatesParts(String baseUrl, File directory) throws Exception {
		byte[] stale = new byte[2048];
		Arrays.fill(stale, (byte) 0x7f);
		Files.write(new File(directory, "multi-layout-change.bin.part0").toPath(), stale);
		writePartMetadata(directory, "multi-layout-change.bin", 3);
		int previous = Global.multiThreadCnt;
		try {
			Global.multiThreadCnt = 4;
			HttpRequestUtilEx util = downloadUtil(directory);
			check(util.download(baseUrl + "/normal", "multi-layout-change.bin", new HashMap<String, String>()),
					"changed part layout must restart safely");
			assertCompletedFile(directory, "multi-layout-change.bin");
			assertNoPartFiles(directory, "multi-layout-change.bin");
		} finally {
			Global.multiThreadCnt = previous;
		}
	}

	private static void testIgnoredRangeFallsBack(String baseUrl, File directory) throws Exception {
		HttpRequestUtilEx util = downloadUtil(directory);
		check(util.download(baseUrl + "/ignore-range", "multi-fallback.bin", new HashMap<String, String>()),
				"server ignoring Range must fall back to a safe single download");
		assertCompletedFile(directory, "multi-fallback.bin");
		assertNoPartFiles(directory, "multi-fallback.bin");
	}

	private static void testInvalidContentRangeRejected(String baseUrl, File directory) {
		HttpRequestUtilEx util = downloadUtil(directory);
		check(!util.download(baseUrl + "/bad-content-range", "multi-bad-range.bin", new HashMap<String, String>()),
				"mismatched Content-Range must fail");
		assertFailedDownloadPreserved(directory, "multi-bad-range.bin");
	}

	private static void testTruncatedPartRejected(String baseUrl, File directory) throws Exception {
		HttpRequestUtilEx util = downloadUtil(directory);
		check(!util.download(baseUrl + "/truncated", "multi-truncated.bin", new HashMap<String, String>()),
				"truncated part response must fail");
		assertFailedDownloadPreserved(directory, "multi-truncated.bin");
		util.init();
		check(util.download(baseUrl + "/normal", "multi-truncated.bin", new HashMap<String, String>()),
				"retry after a truncated part response failed");
		assertCompletedFile(directory, "multi-truncated.bin");
		assertNoPartFiles(directory, "multi-truncated.bin");
	}

	private static void testNonSuccessStatusesRejected(String baseUrl, File directory) {
		for (int status : new int[] { 412, 416 }) {
			String fileName = "multi-status-" + status + ".bin";
			HttpRequestUtilEx util = downloadUtil(directory);
			check(!util.download(baseUrl + "/status-" + status, fileName, new HashMap<String, String>()),
					"HTTP " + status + " part response must fail");
			assertFailedDownloadPreserved(directory, fileName);
		}
	}

	private static void testCancelAndResume(String baseUrl, File directory, RangeHandler handler) throws Exception {
		final HttpRequestUtilEx util = downloadUtil(directory);
		final AtomicReference<Boolean> result = new AtomicReference<Boolean>();
		Thread download = new Thread(new Runnable() {
			@Override
			public void run() {
				result.set(util.download(baseUrl + "/slow", "multi-cancel.bin", new HashMap<String, String>()));
			}
		}, "Thread-MultiContract-Cancel");
		download.start();
		check(handler.firstPartChunk.await(5, TimeUnit.SECONDS), "slow download did not start in time");
		util.stopDownload();
		download.join(15000);
		check(!download.isAlive(), "cancelled multi-thread download did not stop");
		check(Boolean.FALSE.equals(result.get()), "cancelled download must return false");
		check(util.getStatus() == StatusEnum.STOP, "cancelled download must retain STOP status");
		check(!new File(directory, "multi-cancel.bin").exists(), "cancelled final file must not exist");
		check(hasPartFiles(directory, "multi-cancel.bin"), "cancelled part files must remain resumable");

		handler.slow = false;
		util.init();
		check(util.download(baseUrl + "/slow", "multi-cancel.bin", new HashMap<String, String>()),
				"resuming a cancelled multi-thread download failed");
		assertCompletedFile(directory, "multi-cancel.bin");
		assertNoPartFiles(directory, "multi-cancel.bin");
	}

	private static void writePartMetadata(File directory, String name, int threadCount) throws IOException {
		String value = "v1\n" + PAYLOAD.length + "\n" + threadCount + "\n";
		Files.write(new File(directory, name + ".part.meta").toPath(), value.getBytes("UTF-8"));
	}

	private static void testThreadCountBounded(String baseUrl, File directory, RangeHandler handler) throws Exception {
		int previous = Global.multiThreadCnt;
		int requestsBefore = handler.requests.get();
		try {
			Global.multiThreadCnt = 1000;
			HttpRequestUtilEx util = downloadUtil(directory);
			check(util.download(baseUrl + "/normal", "multi-bounded.bin", new HashMap<String, String>()),
					"bounded multi-thread download failed");
			assertCompletedFile(directory, "multi-bounded.bin");
			check(handler.requests.get() - requestsBefore == 17, "worker count must be capped at 16 plus one probe");
		} finally {
			Global.multiThreadCnt = previous;
		}
	}

	private static void testPathEscapeRejected(String baseUrl, File directory) {
		HttpRequestUtilEx util = downloadUtil(directory);
		try {
			util.download(baseUrl + "/normal", "../multi-outside.bin", new HashMap<String, String>());
			throw new AssertionError("multi-thread path escape must be rejected");
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	private static HttpRequestUtilEx downloadUtil(File directory) {
		HttpRequestUtilEx util = new HttpRequestUtilEx();
		util.setSavePath(directory.getAbsolutePath());
		return util;
	}

	private static void assertCompletedFile(File directory, String name) throws Exception {
		File file = new File(directory, name);
		check(file.isFile(), "completed file is missing: " + name);
		check(Arrays.equals(PAYLOAD, Files.readAllBytes(file.toPath())), "completed file content mismatch: " + name);
	}

	private static void assertFailedDownloadPreserved(File directory, String name) {
		check(!new File(directory, name).exists(), "failed download must not publish a final file: " + name);
		check(hasPartFiles(directory, name), "failed download must preserve resumable part files: " + name);
		check(!new File(directory, name + ".part.merge").exists(), "failed merge staging file must not remain");
	}

	private static boolean hasPartFiles(File directory, String name) {
		File[] files = directory.listFiles();
		if (files == null)
			return false;
		Pattern numberedPart = Pattern.compile(Pattern.quote(name) + "\\.part[0-9]+");
		for (File file : files) {
			if (numberedPart.matcher(file.getName()).matches())
				return true;
		}
		return false;
	}

	private static void assertNoPartFiles(File directory, String name) {
		File[] files = directory.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			check(!file.getName().startsWith(name + ".part"), "successful download left a part file: " + file.getName());
		}
	}

	private static void deleteTestDirectory(File directory) throws IOException {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files)
				Files.deleteIfExists(file.toPath());
		}
		Files.deleteIfExists(directory.toPath());
	}

	private static byte[] createPayload(int length) {
		ByteArrayOutputStream output = new ByteArrayOutputStream(length);
		for (int index = 0; index < length; index++)
			output.write((index * 31 + 17) & 0xff);
		return output.toByteArray();
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private enum Mode {
		NORMAL, IGNORE_RANGE, BAD_CONTENT_RANGE, TRUNCATED, STATUS_412, STATUS_416
	}

	private static final class RangeHandler implements HttpHandler {
		private static final AtomicBoolean sawKnownResumeRange = new AtomicBoolean();
		private final Mode mode;
		private final AtomicInteger requests = new AtomicInteger();
		private final CountDownLatch firstPartChunk = new CountDownLatch(1);
		private volatile boolean slow;

		private RangeHandler(Mode mode) {
			this.mode = mode;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			requests.incrementAndGet();
			String range = exchange.getRequestHeaders().getFirst("Range");
			if (mode == Mode.IGNORE_RANGE || range == null) {
				send(exchange, 200, PAYLOAD);
				return;
			}
			Matcher matcher = REQUEST_RANGE.matcher(range);
			if (!matcher.matches()) {
				sendWithoutBody(exchange, 416);
				return;
			}
			int start = Integer.parseInt(matcher.group(1));
			int end = Integer.parseInt(matcher.group(2));
			if (start == 2048)
				sawKnownResumeRange.set(true);
			if (start < 0 || end < start || end >= PAYLOAD.length) {
				sendWithoutBody(exchange, 416);
				return;
			}
			boolean probe = start == 0 && end == 0;
			if (!probe && mode == Mode.STATUS_412) {
				sendWithoutBody(exchange, 412);
				return;
			}
			if (!probe && mode == Mode.STATUS_416) {
				sendWithoutBody(exchange, 416);
				return;
			}

			byte[] body = Arrays.copyOfRange(PAYLOAD, start, end + 1);
			int headerStart = !probe && mode == Mode.BAD_CONTENT_RANGE ? start + 1 : start;
			exchange.getResponseHeaders().set("Content-Range",
					"bytes " + headerStart + "-" + end + "/" + PAYLOAD.length);
			if (!probe && mode == Mode.TRUNCATED) {
				sendTruncated(exchange, body);
				return;
			}
			if (slow && !probe) {
				sendSlow(exchange, body);
				return;
			}
			send(exchange, 206, body);
		}

		private void sendSlow(HttpExchange exchange, byte[] body) throws IOException {
			exchange.sendResponseHeaders(206, body.length);
			OutputStream output = exchange.getResponseBody();
			try {
				for (int offset = 0; offset < body.length; offset += 256) {
					int length = Math.min(256, body.length - offset);
					output.write(body, offset, length);
					output.flush();
					firstPartChunk.countDown();
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			} finally {
				try {
					output.close();
				} catch (IOException ignored) {
				}
				exchange.close();
			}
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

	private static void sendWithoutBody(HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

	private static void sendTruncated(HttpExchange exchange, byte[] body) throws IOException {
		exchange.sendResponseHeaders(206, 0);
		try {
			exchange.getResponseBody().write(body, 0, Math.max(0, body.length - 7));
		} finally {
			try {
				exchange.getResponseBody().close();
			} catch (IOException ignored) {
			}
			exchange.close();
		}
	}
}
