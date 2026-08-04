package nicelee.ui.task;

/**
 * 下载任务的线程安全生命周期。
 *
 * <p>每次提交都会获得唯一令牌。暂停、取消或手动重试会使旧令牌失效，
 * 从而避免队列中的过期任务和自动重试重复执行。</p>
 */
public final class DownloadTaskLifecycle {

	public static final long NO_TOKEN = 0L;
	private static final String DEFAULT_FAILURE_TYPE = "DownloadFailed";

	private DownloadTaskState state = DownloadTaskState.PREPARING;
	private long sequence;
	private long activeSubmissionToken;
	private long scheduledRetryToken;
	private int retryCount;
	private long retryAtMillis;
	private String failureType;

	public synchronized void markQueued() {
		if (state == DownloadTaskState.PREPARING) {
			state = DownloadTaskState.QUEUED;
		}
	}

	public synchronized long claimInitialSubmission() {
		if (state != DownloadTaskState.QUEUED || activeSubmissionToken != NO_TOKEN) {
			return NO_TOKEN;
		}
		return claimSubmission();
	}

	public synchronized long claimManualRetry() {
		if ((state != DownloadTaskState.PAUSED && state != DownloadTaskState.FAILED
				&& state != DownloadTaskState.RETRYING) || activeSubmissionToken != NO_TOKEN) {
			return NO_TOKEN;
		}
		invalidatePendingWork();
		retryCount = 0;
		retryAtMillis = 0L;
		failureType = null;
		state = DownloadTaskState.RETRYING;
		return claimSubmission();
	}

	public synchronized long claimScheduledRetry(long retryToken, long nowMillis) {
		if (state != DownloadTaskState.RETRYING || retryToken == NO_TOKEN
				|| retryToken != scheduledRetryToken || activeSubmissionToken != NO_TOKEN
				|| nowMillis < retryAtMillis) {
			return NO_TOKEN;
		}
		scheduledRetryToken = NO_TOKEN;
		return claimSubmission();
	}

	public synchronized boolean beginExecution(long submissionToken) {
		return isActive(submissionToken)
				&& (state == DownloadTaskState.QUEUED || state == DownloadTaskState.RETRYING);
	}

	public synchronized boolean markDownloading(long submissionToken) {
		if (!isActive(submissionToken)
				|| (state != DownloadTaskState.QUEUED && state != DownloadTaskState.RETRYING)) {
			return false;
		}
		state = DownloadTaskState.DOWNLOADING;
		return true;
	}

	public synchronized void markMerging() {
		if (state == DownloadTaskState.DOWNLOADING) {
			state = DownloadTaskState.MERGING;
		}
	}

	public synchronized boolean markSucceeded(long submissionToken) {
		if (!isActive(submissionToken)) {
			return false;
		}
		activeSubmissionToken = NO_TOKEN;
		scheduledRetryToken = NO_TOKEN;
		retryAtMillis = 0L;
		failureType = null;
		state = DownloadTaskState.SUCCEEDED;
		return true;
	}

	public synchronized RetrySchedule markFailed(long submissionToken, int maxRetries, long nowMillis,
			long retryDelayMillis, String errorType) {
		if (!isActive(submissionToken)) {
			return RetrySchedule.none();
		}
		activeSubmissionToken = NO_TOKEN;
		if (state == DownloadTaskState.PAUSED || state == DownloadTaskState.CANCELLED) {
			return RetrySchedule.none();
		}
		failureType = sanitizeFailureType(errorType);
		int normalizedMaxRetries = Math.max(0, maxRetries);
		if (retryCount < normalizedMaxRetries) {
			retryCount++;
			retryAtMillis = nowMillis + Math.max(0L, retryDelayMillis);
			state = DownloadTaskState.RETRYING;
			scheduledRetryToken = nextToken();
			return new RetrySchedule(scheduledRetryToken, retryAtMillis, retryCount);
		}
		retryAtMillis = 0L;
		scheduledRetryToken = NO_TOKEN;
		state = DownloadTaskState.FAILED;
		return RetrySchedule.none();
	}

	public synchronized void markSubmissionRejected(long submissionToken) {
		if (!isActive(submissionToken)) {
			return;
		}
		activeSubmissionToken = NO_TOKEN;
		scheduledRetryToken = NO_TOKEN;
		retryAtMillis = 0L;
		failureType = "QueueUnavailable";
		state = DownloadTaskState.FAILED;
	}

	public synchronized void markRetrySchedulingRejected(long retryToken) {
		if (state != DownloadTaskState.RETRYING || retryToken == NO_TOKEN
				|| retryToken != scheduledRetryToken) {
			return;
		}
		scheduledRetryToken = NO_TOKEN;
		retryAtMillis = 0L;
		failureType = "RetrySchedulerUnavailable";
		state = DownloadTaskState.FAILED;
	}

	public synchronized void pause() {
		if (state == DownloadTaskState.SUCCEEDED || state == DownloadTaskState.CANCELLED) {
			return;
		}
		invalidatePendingWork();
		retryAtMillis = 0L;
		state = DownloadTaskState.PAUSED;
	}

	public synchronized void cancel() {
		invalidatePendingWork();
		retryAtMillis = 0L;
		state = DownloadTaskState.CANCELLED;
	}

	public synchronized Snapshot snapshot() {
		return new Snapshot(state, retryCount, retryAtMillis, failureType,
				activeSubmissionToken != NO_TOKEN);
	}

	private long claimSubmission() {
		if (state == DownloadTaskState.RETRYING) {
			state = DownloadTaskState.QUEUED;
		}
		activeSubmissionToken = nextToken();
		return activeSubmissionToken;
	}

	private boolean isActive(long submissionToken) {
		return submissionToken != NO_TOKEN && submissionToken == activeSubmissionToken;
	}

	private void invalidatePendingWork() {
		nextToken();
		activeSubmissionToken = NO_TOKEN;
		scheduledRetryToken = NO_TOKEN;
	}

	private long nextToken() {
		sequence++;
		if (sequence == NO_TOKEN) {
			sequence++;
		}
		return sequence;
	}

	private static String sanitizeFailureType(String value) {
		if (value == null || !value.matches("[A-Za-z0-9_.-]{1,64}")) {
			return DEFAULT_FAILURE_TYPE;
		}
		return value;
	}

	public static final class RetrySchedule {
		private static final RetrySchedule NONE = new RetrySchedule(NO_TOKEN, 0L, 0);

		private final long token;
		private final long retryAtMillis;
		private final int retryCount;

		private RetrySchedule(long token, long retryAtMillis, int retryCount) {
			this.token = token;
			this.retryAtMillis = retryAtMillis;
			this.retryCount = retryCount;
		}

		public static RetrySchedule none() {
			return NONE;
		}

		public boolean shouldRetry() {
			return token != NO_TOKEN;
		}

		public long getToken() {
			return token;
		}

		public long getRetryAtMillis() {
			return retryAtMillis;
		}

		public int getRetryCount() {
			return retryCount;
		}
	}

	public static final class Snapshot {
		private final DownloadTaskState state;
		private final int retryCount;
		private final long retryAtMillis;
		private final String failureType;
		private final boolean submissionPending;

		private Snapshot(DownloadTaskState state, int retryCount, long retryAtMillis, String failureType,
				boolean submissionPending) {
			this.state = state;
			this.retryCount = retryCount;
			this.retryAtMillis = retryAtMillis;
			this.failureType = failureType;
			this.submissionPending = submissionPending;
		}

		public DownloadTaskState getState() {
			return state;
		}

		public int getRetryCount() {
			return retryCount;
		}

		public long getRetryAtMillis() {
			return retryAtMillis;
		}

		public String getFailureType() {
			return failureType;
		}

		public boolean isSubmissionPending() {
			return submissionPending;
		}
	}
}
