package nicelee.ui.util;

import java.util.Locale;

public final class DownloadStatusFormatter {

	private static final long KB = 1024L;
	private static final long MB = KB * 1024L;
	private static final long GB = MB * 1024L;

	private DownloadStatusFormatter() {
	}

	public static long bytesPerSecond(long downloadedDelta, long elapsedMillis) {
		if (downloadedDelta <= 0 || elapsedMillis <= 0) {
			return 0L;
		}
		return downloadedDelta * 1000L / elapsedMillis;
	}

	public static String speed(long bytesPerSecond) {
		if (bytesPerSecond <= 0) {
			return "计算中";
		}
		if (bytesPerSecond >= GB) {
			return String.format(Locale.ROOT, "%.2f GB/s", bytesPerSecond * 1.0 / GB);
		}
		if (bytesPerSecond >= MB) {
			return String.format(Locale.ROOT, "%.2f MB/s", bytesPerSecond * 1.0 / MB);
		}
		if (bytesPerSecond >= KB) {
			return String.format(Locale.ROOT, "%.1f KB/s", bytesPerSecond * 1.0 / KB);
		}
		return bytesPerSecond + " B/s";
	}

	public static String eta(long remainingBytes, long bytesPerSecond) {
		if (remainingBytes <= 0 || bytesPerSecond <= 0) {
			return "ETA --";
		}
		long seconds = (remainingBytes + bytesPerSecond - 1) / bytesPerSecond;
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		long secs = seconds % 60;
		if (hours > 0) {
			return String.format(Locale.ROOT, "ETA %d:%02d:%02d", hours, minutes, secs);
		}
		return String.format(Locale.ROOT, "ETA %02d:%02d", minutes, secs);
	}
}
