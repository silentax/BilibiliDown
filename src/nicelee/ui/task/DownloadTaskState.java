package nicelee.ui.task;

/**
 * 用户可见的下载任务生命周期。
 *
 * <p>该状态与底层下载器的传输状态分离，用于统一队列、重试和 UI 行为。</p>
 */
public enum DownloadTaskState {
	PREPARING,
	QUEUED,
	DOWNLOADING,
	PAUSED,
	RETRYING,
	MERGING,
	SUCCEEDED,
	FAILED,
	CANCELLED
}
