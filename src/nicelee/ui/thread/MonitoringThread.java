package nicelee.ui.thread;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import javax.swing.AbstractButton;
import javax.swing.JLabel;

import nicelee.bilibili.downloaders.IDownloader;
import nicelee.bilibili.enums.StatusEnum;
import nicelee.bilibili.util.Logger;
import nicelee.ui.Audio;
import nicelee.ui.Global;
import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.task.DownloadTaskLifecycle;
import nicelee.ui.task.DownloadTaskState;
import nicelee.ui.util.DownloadStatusFormatter;
import nicelee.ui.util.SwingDispatch;

public class MonitoringThread extends Thread {

	private static final Color LIGHT_GREEN = new Color(153, 214, 92);
	private static final Color LIGHT_RED = new Color(255, 71, 10);
	private static final Color LIGHT_PINK = new Color(255, 122, 122);
	private static final Color LIGHT_ORANGE = new Color(255, 207, 61);
	private static final String AUTO_RENAME_PATTERN =
			"(?:av|h|cv|opus|BV|season|au|edd_)[0-9a-zA-Z_]+-[0-9]+-p[0-9]+";

	private final Set<DownloadInfoPanel> successReported = newConcurrentSet();
	private final Set<DownloadInfoPanel> terminalFailReported = newConcurrentSet();
	private final Set<DownloadInfoPanel> stopReported = newConcurrentSet();
	private final AtomicBoolean uiRefreshScheduled = new AtomicBoolean();
	private volatile UiBatch latestUiBatch;

	public MonitoringThread() {
		setName("Thread-MonitoringDownload");
		setDaemon(true);
	}

	@Override
	public void run() {
		ConcurrentHashMap<DownloadInfoPanel, IDownloader> map = Global.downloadTaskList;
		if (Global.playSoundAfterMissionComplete) {
			Audio.init();
		}
		int lastInProgressTaskCount = 0;
		while (!isInterrupted()) {
			UiBatch batch = collectSnapshot(map);
			publish(batch);

			if (Global.playSoundAfterMissionComplete && lastInProgressTaskCount > 0
					&& batch.activeTask == 0 && batch.retryingTask == 0) {
				Audio.play();
			}
			lastInProgressTaskCount = batch.activeTask + batch.retryingTask;
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				interrupt();
			}
		}
	}

	private UiBatch collectSnapshot(ConcurrentHashMap<DownloadInfoPanel, IDownloader> map) {
		List<UiUpdate> updates = new ArrayList<UiUpdate>();
		int active = 0;
		int paused = 0;
		int retrying = 0;
		int done = 0;
		int queuing = 0;
		int maxFailCount = Global.maxFailRetry;

		for (Entry<DownloadInfoPanel, IDownloader> entry : map.entrySet()) {
			DownloadInfoPanel panel = entry.getKey();
			IDownloader downloader = entry.getValue();
			StatusEnum downloaderStatus;
			try {
				downloaderStatus = downloader.currentStatus();
			} catch (RuntimeException e) {
				downloaderStatus = StatusEnum.NONE;
			}

			try {
				if (downloaderStatus == StatusEnum.PROCESSING) {
					panel.markMerging();
				}
				DownloadTaskLifecycle.Snapshot snapshot = panel.getTaskSnapshot();
				DownloadTaskState state = snapshot.getState();
				String path = resolvePath(panel, downloader, state);
				switch (state) {
				case SUCCEEDED:
					done++;
					String fileSize = IDownloader.transToSizeStr(downloader.sumTotalFileSize());
					updates.add(new UiUpdate(panel, path, tips("%d/%d 下载完成. ", downloader),
							"文件大小: " + fileSize, "暂停", false, LIGHT_GREEN));
					if (successReported.add(panel)) {
						BatchDownloadRbyRThread.taskSucceed(panel.getClipInfo(), panel.formattedTitle, fileSize,
								String.valueOf(panel.getRealqn()));
					}
					break;
				case FAILED:
					paused++;
					stopReported.remove(panel);
					updates.add(new UiUpdate(panel, path, failureText(snapshot), failureAdvice(),
							"重新下载", true, LIGHT_RED));
					if (terminalFailReported.add(panel)) {
						BatchDownloadRbyRThread.taskFail(panel.getClipInfo(), failureType(snapshot));
					}
					break;
				case RETRYING:
					retrying++;
					terminalFailReported.remove(panel);
					stopReported.remove(panel);
					long remainingMillis = Math.max(0L, snapshot.getRetryAtMillis() - System.currentTimeMillis());
					long remainingSeconds = (remainingMillis + 999L) / 1000L;
					updates.add(new UiUpdate(panel, path,
							String.format("下载失败，%d 秒后自动重试 %d/%d", remainingSeconds,
									snapshot.getRetryCount(), maxFailCount),
							failureAdvice(), "立即重试", true, LIGHT_RED));
					break;
				case PAUSED:
					paused++;
					terminalFailReported.remove(panel);
					updates.add(stopped(panel, path, downloader));
					if (stopReported.add(panel)) {
						BatchDownloadRbyRThread.taskFail(panel.getClipInfo(), "stop");
					}
					break;
				case MERGING:
					active++;
					clearTransientReports(panel);
					updates.add(new UiUpdate(panel, path, tips("%d/%d 转码中... ", downloader),
							"文件大小: " + IDownloader.transToSizeStr(downloader.sumTotalFileSize()), "暂停", false,
							LIGHT_ORANGE));
					break;
				case PREPARING:
					queuing++;
					clearTransientReports(panel);
					updates.add(new UiUpdate(panel, path, "正在准备下载...", "正在获取下载地址...", "暂停", false,
							LIGHT_ORANGE));
					break;
				case QUEUED:
					queuing++;
					clearTransientReports(panel);
					updates.add(new UiUpdate(panel, path, "等待下载中...", "等待下载中...", "暂停", true,
							LIGHT_ORANGE));
					break;
				case DOWNLOADING:
					active++;
					clearTransientReports(panel);
					updates.add(downloading(panel, path, downloader));
					break;
				case CANCELLED:
					paused++;
					updates.add(new UiUpdate(panel, path, "任务已取消", "临时文件将被清理", "重新下载", false,
							LIGHT_PINK));
					break;
				default:
					break;
				}
			} catch (RuntimeException e) {
				Logger.println("刷新下载状态失败: " + e.getClass().getSimpleName());
				DownloadTaskState state = panel.getTaskSnapshot().getState();
				if (state == DownloadTaskState.PAUSED || state == DownloadTaskState.CANCELLED) {
					paused++;
					updates.add(new UiUpdate(panel, null, "任务已停止", "任务已停止", "继续下载", true, LIGHT_PINK));
				} else if (state == DownloadTaskState.MERGING) {
					active++;
					updates.add(new UiUpdate(panel, null, "转码中...", "正在处理文件", "暂停", false, LIGHT_ORANGE));
				} else {
					queuing++;
					updates.add(new UiUpdate(panel, null, "等待下载中...", "等待下载中...", "暂停", false,
							LIGHT_ORANGE));
				}
			}
		}

		successReported.retainAll(map.keySet());
		terminalFailReported.retainAll(map.keySet());
		stopReported.retainAll(map.keySet());
		return new UiBatch(updates, map.size(), active, paused, done, queuing, retrying);
	}

	private UiUpdate downloading(DownloadInfoPanel panel, String path, IDownloader downloader) {
		long now = System.currentTimeMillis();
		long downloaded = downloader.sumDownloadedFileSize();
		long elapsed = panel.getLastCntTime() == 0L ? 0L : now - panel.getLastCntTime();
		long delta = panel.getLastCntTime() == 0L ? 0L : downloaded - panel.getLastCnt();
		long speed = DownloadStatusFormatter.bytesPerSecond(delta, elapsed);
		panel.setLastCnt(downloaded);
		panel.setLastCntTime(now);
		long remaining = Math.max(0L, downloader.currentFileTotalSize() - downloader.currentFileDownloadedSize());
		String text = String.format("%d/%d 正在下载... %s / %s", downloader.currentTask(),
				downloader.totalTaskCount(), DownloadStatusFormatter.speed(speed), DownloadStatusFormatter.eta(remaining, speed));
		return new UiUpdate(panel, path, text, sizeProgress(downloader), "暂停", true, null);
	}

	private UiUpdate stopped(DownloadInfoPanel panel, String path, IDownloader downloader) {
		return new UiUpdate(panel, path, tips("%d/%d 人工停止. ", downloader), sizeProgress(downloader),
				"继续下载", true, LIGHT_PINK);
	}

	private String resolvePath(DownloadInfoPanel panel, IDownloader downloader, DownloadTaskState state) {
		File file = downloader.file();
		if (file == null) {
			return null;
		}
		String path = file.getAbsolutePath();
		if (Global.doRenameAfterComplete && state == DownloadTaskState.SUCCEEDED && panel.formattedTitle != null) {
			path = path.replaceFirst(AUTO_RENAME_PATTERN, Matcher.quoteReplacement(panel.formattedTitle));
		}
		return path;
	}

	private void clearTransientReports(DownloadInfoPanel panel) {
		terminalFailReported.remove(panel);
		stopReported.remove(panel);
	}

	private void publish(UiBatch batch) {
		latestUiBatch = batch;
		if (uiRefreshScheduled.compareAndSet(false, true)) {
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					drainUiRefresh();
				}
			});
		}
	}

	private void drainUiRefresh() {
		UiBatch batch = latestUiBatch;
		latestUiBatch = null;
		if (batch != null) {
			for (UiUpdate update : batch.updates) {
				update.apply();
			}
			if (Global.downloadTab != null) {
				Global.downloadTab.refreshStatus(batch.totalTask, batch.activeTask, batch.pausedTask,
						batch.doneTask, batch.queuingTask, batch.retryingTask);
			}
		}
		uiRefreshScheduled.set(false);
		if (latestUiBatch != null) {
			publish(latestUiBatch);
		}
	}

	private static String tips(String format, IDownloader downloader) {
		return String.format(format, downloader.currentTask(), downloader.totalTaskCount());
	}

	private static String sizeProgress(IDownloader downloader) {
		return String.format("文件%d进度： %s/%s", downloader.currentTask(),
				IDownloader.transToSizeStr(downloader.currentFileDownloadedSize()),
				IDownloader.transToSizeStr(downloader.currentFileTotalSize()));
	}

	private static String failureText(DownloadTaskLifecycle.Snapshot snapshot) {
		return "下载失败（" + failureType(snapshot) + "）";
	}

	private static String failureAdvice() {
		return "建议检查网络、登录状态和资源可用性后重试";
	}

	private static String failureType(DownloadTaskLifecycle.Snapshot snapshot) {
		return snapshot.getFailureType() == null ? "DownloadFailed" : snapshot.getFailureType();
	}

	private static Set<DownloadInfoPanel> newConcurrentSet() {
		return Collections.newSetFromMap(new ConcurrentHashMap<DownloadInfoPanel, Boolean>());
	}

	private static final class UiBatch {
		private final List<UiUpdate> updates;
		private final int totalTask;
		private final int activeTask;
		private final int pausedTask;
		private final int doneTask;
		private final int queuingTask;
		private final int retryingTask;

		private UiBatch(List<UiUpdate> updates, int totalTask, int activeTask, int pausedTask, int doneTask,
				int queuingTask, int retryingTask) {
			this.updates = updates;
			this.totalTask = totalTask;
			this.activeTask = activeTask;
			this.pausedTask = pausedTask;
			this.doneTask = doneTask;
			this.queuingTask = queuingTask;
			this.retryingTask = retryingTask;
		}
	}

	private static final class UiUpdate {
		private final DownloadInfoPanel panel;
		private final String fileName;
		private final String status;
		private final String progress;
		private final String buttonText;
		private final boolean buttonVisible;
		private final Color background;

		private UiUpdate(DownloadInfoPanel panel, String fileName, String status, String progress, String buttonText,
				boolean buttonVisible, Color background) {
			this.panel = panel;
			this.fileName = fileName;
			this.status = status;
			this.progress = progress;
			this.buttonText = buttonText;
			this.buttonVisible = buttonVisible;
			this.background = background;
		}

		private void apply() {
			if (fileName != null) {
				setText(panel.getLbFileName(), fileName);
				if (!Objects.equals(panel.getLbFileName().getToolTipText(), fileName)) {
					panel.getLbFileName().setToolTipText(fileName);
				}
			}
			setText(panel.getLbCurrentStatus(), status);
			setText(panel.getLbDownFile(), progress);
			setText(panel.getBtnControl(), buttonText);
			if (panel.getBtnControl().isVisible() != buttonVisible) {
				panel.getBtnControl().setVisible(buttonVisible);
			}
			if (!Objects.equals(panel.getBackground(), background)) {
				panel.setBackground(background);
			}
		}

		private static void setText(JLabel label, String text) {
			if (!Objects.equals(label.getText(), text)) {
				label.setText(text);
			}
		}

		private static void setText(AbstractButton button, String text) {
			if (!Objects.equals(button.getText(), text)) {
				button.setText(text);
			}
		}
	}
}
