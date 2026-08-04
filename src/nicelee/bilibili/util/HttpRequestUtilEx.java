package nicelee.bilibili.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.bilibili.enums.StatusEnum;
import nicelee.ui.Global;

/**
 * 支持断点续传的有界多线程下载器。只有服务端明确支持标准 Range 响应时才会
 * 启动分片线程；否则安全回退到 {@link HttpRequestUtil} 的单线程实现。
 */
public class HttpRequestUtilEx extends HttpRequestUtil {

	private static final int MAX_MULTI_THREAD_COUNT = 16;
	private static final String PART_LAYOUT_VERSION = "v1";
	private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile(
			"^bytes\\s+([0-9]+)-([0-9]+)/([0-9]+)$", Pattern.CASE_INSENSITIVE);

	@Override
	public boolean download(String url, String fileName, HashMap<String, String> headers) {
		if (!shouldUseMultiThread(url))
			return super.download(url, fileName, headers);
		Boolean existingResult = check(fileName);
		if (existingResult != null) {
			if (Boolean.TRUE.equals(existingResult))
				clearMultiPartStateQuietly();
			return existingResult;
		}

		RangeProbe probe = probeRangeSupport(url, headers);
		if (probe.failed) {
			status = StatusEnum.FAIL;
			return false;
		}
		if (!probe.supported) {
			Logger.println("服务端不支持标准 Range，回退到单线程下载");
			clearMultiPartStateQuietly();
			return super.download(url, fileName, probe.headers);
		}
		totalFileSize = probe.totalSize;
		if (Global.multiThreadMinFileSize > 0 && totalFileSize <= Global.multiThreadMinFileSize) {
			Logger.println("文件小于多线程阈值，回退到单线程下载");
			clearMultiPartStateQuietly();
			return super.download(url, fileName, probe.headers);
		}

		int requestedThreads = Math.max(2, Global.multiThreadCnt);
		int threadCount = (int) Math.min(totalFileSize, Math.min(requestedThreads, MAX_MULTI_THREAD_COUNT));
		List<PartSpec> parts;
		try {
			parts = prepareParts(threadCount, totalFileSize);
		} catch (IOException e) {
			Logger.println("准备下载分片失败: " + e.getClass().getSimpleName());
			status = StatusEnum.FAIL;
			return false;
		}

		downloadedFileSize.set(sumExistingBytes(parts));
		AtomicBoolean failed = new AtomicBoolean(false);
		CountDownLatch completed = new CountDownLatch(parts.size());
		for (PartSpec part : parts) {
			Thread worker = new Thread(new PartDownload(part, url, probe.headers, failed, completed),
					"Thread-MultiDownload-" + part.index);
			worker.setDaemon(true);
			worker.start();
		}

		boolean interrupted = awaitWorkers(completed);
		if (interrupted) {
			Thread.currentThread().interrupt();
			status = StatusEnum.STOP;
			return false;
		}
		if (!bDown || status == StatusEnum.STOP)
			return false;
		if (failed.get()) {
			status = StatusEnum.FAIL;
			return false;
		}

		try {
			mergeAndPublish(parts, fileDownload, totalFileSize);
			downloadedFileSize.set(totalFileSize);
			status = StatusEnum.SUCCESS;
			Logger.println("多线程下载完成");
			return true;
		} catch (IOException e) {
			Logger.println("合并下载分片失败: " + e.getClass().getSimpleName());
			status = StatusEnum.FAIL;
			return false;
		}
	}

	private boolean shouldUseMultiThread(String url) {
		if (Global.multiThreadCnt <= 1)
			return false;
		return Global.singleThreadPattern == null || !Global.singleThreadPattern.matcher(url).find();
	}

	/**
	 * 返回文件总大小；0 表示服务端不支持标准 Range，-1 表示探测失败。
	 */
	public long getTotalSize(String url, HashMap<String, String> headers) {
		RangeProbe probe = probeRangeSupport(url, headers);
		if (probe.failed)
			return -1;
		return probe.supported ? probe.totalSize : 0;
	}

	private RangeProbe probeRangeSupport(String url, HashMap<String, String> headers) {
		HashMap<String, String> requestHeaders = copyHeaders(headers);
		removeHeaderIgnoreCase(requestHeaders, "range");
		requestHeaders.put("Range", "bytes=0-0");
		RangeProbe first = executeRangeProbe(url, requestHeaders);
		if (first.responseCode != HttpURLConnection.HTTP_FORBIDDEN)
			return first;

		HashMap<String, String> alternativeHeaders = HttpHeaders.getBiliAppDownHeaders();
		removeHeaderIgnoreCase(alternativeHeaders, "range");
		alternativeHeaders.put("Range", "bytes=0-0");
		return executeRangeProbe(url, alternativeHeaders);
	}

	private RangeProbe executeRangeProbe(String url, HashMap<String, String> headers) {
		HttpURLConnection connection = null;
		InputStream input = null;
		try {
			connection = connect(headers, url, null);
			connection.connect();
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
				ContentRange contentRange = parseContentRange(connection.getHeaderField("Content-Range"));
				if (contentRange.start != 0 || contentRange.end != 0 || contentRange.total <= 0)
					throw new IOException("Range 探测响应不匹配");
				long contentLength = connection.getContentLengthLong();
				if (contentLength >= 0 && contentLength != 1)
					throw new IOException("Range 探测长度不匹配");
				input = connection.getInputStream();
				if (input.read() == -1)
					throw new IOException("Range 探测响应为空");
				return RangeProbe.supported(contentRange.total, headers, responseCode);
			}
			if (responseCode >= 200 && responseCode < 300)
				return RangeProbe.unsupported(headers, responseCode);
			return RangeProbe.failed(headers, responseCode);
		} catch (Exception e) {
			Logger.println("Range 能力探测失败: " + e.getClass().getSimpleName());
			return RangeProbe.failed(headers, -1);
		} finally {
			ResourcesUtil.closeQuietly(input);
			if (connection != null)
				connection.disconnect();
		}
	}

	private List<PartSpec> prepareParts(int threadCount, long totalSize) throws IOException {
		preparePartLayout(threadCount, totalSize);
		List<PartSpec> parts = new ArrayList<PartSpec>(threadCount);
		long baseSize = totalSize / threadCount;
		long remainder = totalSize % threadCount;
		long start = 0;
		for (int index = 0; index < threadCount; index++) {
			long length = baseSize + (index < remainder ? 1 : 0);
			long end = start + length - 1;
			File partFile = new File(fileDownload.getParentFile(), fileDownload.getName() + ".part" + index);
			if (partFile.length() > length) {
				RandomAccessFile oversized = new RandomAccessFile(partFile, "rw");
				try {
					oversized.setLength(0);
				} finally {
					oversized.close();
				}
			}
			parts.add(new PartSpec(index, start, end, partFile));
			start = end + 1;
		}
		return parts;
	}

	private void preparePartLayout(int threadCount, long totalSize) throws IOException {
		File metadata = partMetadataFile();
		boolean reusable = metadataMatches(metadata, threadCount, totalSize);
		if (!reusable)
			clearMultiPartState();
		Files.deleteIfExists(mergeStagingFile().toPath());
		if (!reusable)
			writePartMetadata(metadata, threadCount, totalSize);
	}

	private boolean metadataMatches(File metadata, int threadCount, long totalSize) {
		if (!metadata.isFile())
			return false;
		try {
			List<String> lines = Files.readAllLines(metadata.toPath(), StandardCharsets.UTF_8);
			return lines.size() == 3 && PART_LAYOUT_VERSION.equals(lines.get(0))
					&& totalSize == Long.parseLong(lines.get(1)) && threadCount == Integer.parseInt(lines.get(2));
		} catch (Exception e) {
			return false;
		}
	}

	private void writePartMetadata(File metadata, int threadCount, long totalSize) throws IOException {
		File temporary = new File(metadata.getParentFile(), metadata.getName() + ".new");
		String value = PART_LAYOUT_VERSION + "\n" + totalSize + "\n" + threadCount + "\n";
		Files.write(temporary.toPath(), value.getBytes(StandardCharsets.UTF_8));
		try {
			Files.move(temporary.toPath(), metadata.toPath(), StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private long sumExistingBytes(List<PartSpec> parts) {
		long total = 0;
		for (PartSpec part : parts)
			total += part.file.length();
		return total;
	}

	private boolean awaitWorkers(CountDownLatch completed) {
		boolean interrupted = false;
		while (true) {
			try {
				completed.await();
				return interrupted;
			} catch (InterruptedException e) {
				interrupted = true;
				bDown = false;
			}
		}
	}

	private void mergeAndPublish(List<PartSpec> parts, File destination, long expectedSize) throws IOException {
		File staging = mergeStagingFile();
		RandomAccessFile output = null;
		IOException failure = null;
		try {
			output = new RandomAccessFile(staging, "rw");
			output.setLength(0);
			byte[] mergeBuffer = new byte[1024 * 1024];
			for (PartSpec part : parts) {
				if (part.file.length() != part.length())
					throw new IOException("下载分片长度不匹配");
				RandomAccessFile input = new RandomAccessFile(part.file, "r");
				try {
					int read;
					while ((read = input.read(mergeBuffer)) != -1)
						output.write(mergeBuffer, 0, read);
				} finally {
					input.close();
				}
			}
			if (output.length() != expectedSize)
				throw new IOException("合并文件长度不匹配");
		} catch (IOException e) {
			failure = e;
		} finally {
			ResourcesUtil.closeQuietly(output);
		}
		if (failure != null) {
			Files.deleteIfExists(staging.toPath());
			throw failure;
		}

		try {
			moveCompletedDownload(staging, destination);
		} catch (IOException e) {
			Files.deleteIfExists(staging.toPath());
			throw e;
		}
		for (PartSpec part : parts)
			deleteQuietly(part.file);
		deleteQuietly(partMetadataFile());
	}

	private File mergeStagingFile() {
		return new File(fileDownload.getParentFile(), fileDownload.getName() + ".part.merge");
	}

	private File partMetadataFile() {
		return new File(fileDownload.getParentFile(), fileDownload.getName() + ".part.meta");
	}

	private void clearMultiPartStateQuietly() {
		try {
			clearMultiPartState();
		} catch (IOException e) {
			Logger.println("清理旧下载分片失败: " + e.getClass().getSimpleName());
		}
	}

	private void clearMultiPartState() throws IOException {
		File[] files = fileDownload.getParentFile().listFiles();
		if (files == null)
			throw new IOException("无法读取下载目录");
		Pattern partPattern = Pattern.compile(Pattern.quote(fileDownload.getName()) + "\\.part[0-9]+");
		for (File file : files) {
			String name = file.getName();
			if (partPattern.matcher(name).matches() || name.equals(fileDownload.getName() + ".part.meta")
					|| name.equals(fileDownload.getName() + ".part.meta.new")
					|| name.equals(fileDownload.getName() + ".part.merge"))
				Files.deleteIfExists(file.toPath());
		}
	}

	private static void deleteQuietly(File file) {
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			Logger.println("下载完成，但临时文件清理失败: " + e.getClass().getSimpleName());
		}
	}

	private static HashMap<String, String> copyHeaders(HashMap<String, String> headers) {
		return headers == null ? new HashMap<String, String>() : new HashMap<String, String>(headers);
	}

	private static void removeHeaderIgnoreCase(HashMap<String, String> headers, String name) {
		for (Iterator<String> iterator = headers.keySet().iterator(); iterator.hasNext();) {
			if (name.equalsIgnoreCase(iterator.next()))
				iterator.remove();
		}
	}

	private static ContentRange parseContentRange(String value) throws IOException {
		if (value == null)
			throw new IOException("缺少 Content-Range");
		Matcher matcher = CONTENT_RANGE_PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT));
		if (!matcher.matches())
			throw new IOException("Content-Range 格式无效");
		try {
			return new ContentRange(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)),
					Long.parseLong(matcher.group(3)));
		} catch (NumberFormatException e) {
			throw new IOException("Content-Range 数值无效", e);
		}
	}

	private final class PartDownload implements Runnable {
		private final PartSpec part;
		private final String url;
		private final HashMap<String, String> baseHeaders;
		private final AtomicBoolean failed;
		private final CountDownLatch completed;

		private PartDownload(PartSpec part, String url, HashMap<String, String> baseHeaders, AtomicBoolean failed,
				CountDownLatch completed) {
			this.part = part;
			this.url = url;
			this.baseHeaders = baseHeaders;
			this.failed = failed;
			this.completed = completed;
		}

		@Override
		public void run() {
			HttpURLConnection connection = null;
			InputStream input = null;
			RandomAccessFile output = null;
			try {
				long offset = part.file.length();
				if (offset == part.length())
					return;
				long requestStart = part.start + offset;
				HashMap<String, String> requestHeaders = copyHeaders(baseHeaders);
				removeHeaderIgnoreCase(requestHeaders, "range");
				requestHeaders.put("Range", "bytes=" + requestStart + "-" + part.end);

				output = new RandomAccessFile(part.file, "rw");
				output.seek(offset);
				connection = connect(requestHeaders, url, null);
				connection.connect();
				if (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
					throw new IOException("分片请求未返回 HTTP 206");
				ContentRange contentRange = parseContentRange(connection.getHeaderField("Content-Range"));
				if (contentRange.start != requestStart || contentRange.end != part.end
						|| contentRange.total != totalFileSize)
					throw new IOException("分片 Content-Range 不匹配");
				long expectedResponseLength = part.end - requestStart + 1;
				long responseLength = connection.getContentLengthLong();
				if (responseLength >= 0 && responseLength != expectedResponseLength)
					throw new IOException("分片响应长度不匹配");

				input = connection.getInputStream();
				byte[] partBuffer = new byte[1024 * 1024];
				int read;
				while ((read = input.read(partBuffer)) != -1) {
					if (!bDown || failed.get())
						return;
					if (read == 0)
						continue;
					output.write(partBuffer, 0, read);
					downloadedFileSize.addAndGet(read);
				}
				if (part.file.length() != part.length())
					throw new IOException("分片文件长度与预期不一致");
			} catch (Exception e) {
				if (bDown && failed.compareAndSet(false, true)) {
					Logger.println("下载分片失败: " + e.getClass().getSimpleName());
				}
			} finally {
				ResourcesUtil.closeQuietly(input);
				ResourcesUtil.closeQuietly(output);
				if (connection != null)
					connection.disconnect();
				completed.countDown();
			}
		}
	}

	private static final class PartSpec {
		private final int index;
		private final long start;
		private final long end;
		private final File file;

		private PartSpec(int index, long start, long end, File file) {
			this.index = index;
			this.start = start;
			this.end = end;
			this.file = file;
		}

		private long length() {
			return end - start + 1;
		}
	}

	private static final class ContentRange {
		private final long start;
		private final long end;
		private final long total;

		private ContentRange(long start, long end, long total) {
			this.start = start;
			this.end = end;
			this.total = total;
		}
	}

	private static final class RangeProbe {
		private final boolean supported;
		private final boolean failed;
		private final long totalSize;
		private final HashMap<String, String> headers;
		private final int responseCode;

		private RangeProbe(boolean supported, boolean failed, long totalSize, HashMap<String, String> headers,
				int responseCode) {
			this.supported = supported;
			this.failed = failed;
			this.totalSize = totalSize;
			this.headers = copyHeaders(headers);
			this.responseCode = responseCode;
		}

		private static RangeProbe supported(long totalSize, HashMap<String, String> headers, int responseCode) {
			return new RangeProbe(true, false, totalSize, headers, responseCode);
		}

		private static RangeProbe unsupported(HashMap<String, String> headers, int responseCode) {
			return new RangeProbe(false, false, 0, headers, responseCode);
		}

		private static RangeProbe failed(HashMap<String, String> headers, int responseCode) {
			return new RangeProbe(false, true, -1, headers, responseCode);
		}
	}
}
