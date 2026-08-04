package nicelee.test.ui;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JSplitPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.ui.TabDownload;
import nicelee.ui.TabSettings;
import nicelee.ui.TabVideo;
import nicelee.ui.item.ClipInfoPanel;
import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.task.DownloadTaskLifecycle;
import nicelee.ui.task.DownloadTaskState;
import nicelee.ui.thread.DownloadExecutors;
import nicelee.ui.util.DownloadStatusFormatter;
import nicelee.ui.util.SwingDispatch;

public class UiExperienceTest {

	public static void main(String[] args) throws Exception {
		testCallAndWaitUsesEdt();
		testRunLaterUsesEdt();
		testExceptionPropagation();
		testPreparingCounterIsThreadSafe();
		testResponsiveDownloadLayouts();
		testResponsiveVideoLayoutAndFeedback();
		testResponsiveSettingsLayoutAndAsyncSave();
		testBoundedQueryExecutor();
		testDownloadTaskLifecycle();
		testPauseCancelAndQueueRejection();
		testRetrySchedulerAndStateWiring();
		testDownloadProgressFormatting();
		System.out.println("UI experience regression tests passed");
	}

	private static void testCallAndWaitUsesEdt() throws Exception {
		final AtomicBoolean executedOnEdt = new AtomicBoolean(false);
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				Boolean value = SwingDispatch.callAndWait(new Callable<Boolean>() {
					@Override
					public Boolean call() {
						return SwingUtilities.isEventDispatchThread();
					}
				});
				executedOnEdt.set(value.booleanValue());
			}
		}, "ui-test-worker");
		worker.start();
		worker.join(5000L);
		check(!worker.isAlive(), "callAndWait must finish");
		check(executedOnEdt.get(), "callAndWait must execute on EDT");
	}

	private static void testRunLaterUsesEdt() throws Exception {
		final CountDownLatch completed = new CountDownLatch(1);
		final AtomicBoolean executedOnEdt = new AtomicBoolean(false);
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				executedOnEdt.set(SwingUtilities.isEventDispatchThread());
				completed.countDown();
			}
		});
		check(completed.await(5, TimeUnit.SECONDS), "runLater must finish");
		check(executedOnEdt.get(), "runLater must execute on EDT");
	}

	private static void testExceptionPropagation() {
		boolean propagated = false;
		try {
			SwingDispatch.callAndWait(new Callable<Object>() {
				@Override
				public Object call() {
					throw new IllegalArgumentException("expected");
				}
			});
		} catch (IllegalArgumentException e) {
			propagated = "expected".equals(e.getMessage());
		}
		check(propagated, "EDT task exceptions must propagate to caller");
	}

	private static void testPreparingCounterIsThreadSafe() throws Exception {
		final TabDownload tab = SwingDispatch.callAndWait(new Callable<TabDownload>() {
			@Override
			public TabDownload call() {
				return new TabDownload();
			}
		});
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				tab.beginPreparingTask();
				tab.finishPreparingTask();
				tab.finishPreparingTask();
			}
		}, "preparing-counter-test");
		worker.start();
		worker.join(5000L);
		check(!worker.isAlive(), "preparing counter worker must finish");
		check(tab.getPreparingTaskCount() == 0, "preparing counter must not become negative");
	}

	private static void testResponsiveDownloadLayouts() {
		TabDownload tab = SwingDispatch.callAndWait(new Callable<TabDownload>() {
			@Override
			public TabDownload call() {
				return new TabDownload();
			}
		});
		check(tab.getLayout() instanceof BorderLayout, "download tab must resize with BorderLayout");
		check(tab.getJpContent().getLayout() instanceof GridLayout, "download cards must use a vertical grid");

		DownloadInfoPanel panel = SwingDispatch.callAndWait(new Callable<DownloadInfoPanel>() {
			@Override
			public DownloadInfoPanel call() {
				ClipInfo clip = new ClipInfo();
				clip.setAvId("BV-layout-test");
				clip.setAvTitle("布局测试");
				clip.setTitle("分集");
				clip.setcId(1L);
				clip.setPage(1);
				return new DownloadInfoPanel(clip, 80);
			}
		});
		check(panel.getLayout() instanceof GridBagLayout, "download card must distribute horizontal space");
	}

	private static void testResponsiveVideoLayoutAndFeedback() {
		final TabVideo tab = SwingDispatch.callAndWait(new Callable<TabVideo>() {
			@Override
			public TabVideo call() {
				return new TabVideo(new javax.swing.JLabel("正在加载"));
			}
		});
		check(tab.getLayout() instanceof BorderLayout, "video tab must resize with BorderLayout");
		check(tab.getDetailSplitPane().getOrientation() == JSplitPane.HORIZONTAL_SPLIT,
				"video preview and clip list must use a horizontal split pane");
		check(tab.getJpContent().getLayout() instanceof GridLayout, "video clips must use a vertical grid");
		check(tab.getLoadProgress().isVisible(), "video parsing progress must be visible while loading");
		check(!tab.areDownloadActionsEnabled(), "video download actions must be disabled while loading");

		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				tab.beginRenderingClips(3);
				tab.updateRenderingProgress(2, 3);
			}
		});
		check(tab.getLoadStatusLabel().getText().contains("2 / 3"), "clip rendering progress must be explicit");
		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				tab.completeLoading(3);
			}
		});
		check(!tab.getLoadProgress().isVisible(), "video progress must stop after parsing");
		check(tab.areDownloadActionsEnabled(), "video download actions must be enabled after parsing");
		check(tab.getLoadStatusLabel().getText().contains("3 个分集"), "video result count must be visible");

		ClipInfoPanel clipPanel = SwingDispatch.callAndWait(new Callable<ClipInfoPanel>() {
			@Override
			public ClipInfoPanel call() {
				ClipInfo clip = new ClipInfo();
				clip.setAvId("BV-layout-test");
				clip.setAvTitle("作品布局测试");
				clip.setTitle("长标题分集");
				clip.setPage(1);
				clip.setLinks(new HashMap<Integer, String>());
				return new ClipInfoPanel(new VideoInfo(), clip, tab);
			}
		});
		check(clipPanel.getLayout() instanceof BorderLayout, "clip card must resize with BorderLayout");

		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				tab.setLoadFailed("解析失败测试");
			}
		});
		check(!tab.areDownloadActionsEnabled(), "video download actions must stay disabled after failure");
		check("解析失败测试".equals(tab.getLoadStatusLabel().getText()), "video failure reason must be visible");
	}

	private static void testResponsiveSettingsLayoutAndAsyncSave() throws Exception {
		TabSettings tab = SwingDispatch.callAndWait(new Callable<TabSettings>() {
			@Override
			public TabSettings call() {
				return new TabSettings();
			}
		});
		check(tab.getLayout() instanceof BorderLayout, "settings tab must resize with BorderLayout");
		check(tab.getSettingsContentPanel().getLayout() instanceof GridBagLayout,
				"settings rows must resize with GridBagLayout");
		check(tab.getSettingsContentPanel() instanceof Scrollable
				&& ((Scrollable) tab.getSettingsContentPanel()).getScrollableTracksViewportWidth(),
				"settings content must track the viewport width");
		check(tab.getEditorCount() > 0, "settings must create explicit editor bindings");
		check(tab.isSensitiveEditorMasked("bilibili.download.push.token"),
				"sensitive settings must not be displayed as plain text");
		check(tab.getSettingsScrollPane().getHorizontalScrollBarPolicy() == javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
				"settings must not depend on a fixed horizontal canvas");

		String source = new String(Files.readAllBytes(Paths.get("src/nicelee/ui/TabSettings.java")),
				StandardCharsets.UTF_8);
		check(!source.contains("1150"), "settings must not restore the 1150px fixed canvas");
		check(!source.contains("i += 3"), "settings save must not depend on component triplets");
		check(source.contains("Thread-SettingsSave"), "settings file I/O must run in a background thread");
		check(source.contains("ConfigUtil.saveConfig(settingsSnapshot)"),
				"settings save must use an immutable UI snapshot");
	}

	private static void testBoundedQueryExecutor() throws Exception {
		check(DownloadExecutors.normalizeQueryPoolSize(0) == 1, "query concurrency lower bound mismatch");
		check(DownloadExecutors.normalizeQueryPoolSize(99) == 4, "query concurrency upper bound mismatch");
		ExecutorService executor = DownloadExecutors.newQueryThreadPool(2);
		try {
			ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
			check(pool.getCorePoolSize() == 2, "query executor must honor configured concurrency");
			final CountDownLatch started = new CountDownLatch(2);
			final CountDownLatch release = new CountDownLatch(1);
			Runnable blocker = new Runnable() {
				@Override
				public void run() {
					started.countDown();
					try {
						release.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			};
			executor.execute(blocker);
			executor.execute(blocker);
			check(started.await(5, TimeUnit.SECONDS), "query workers must start concurrently");
			for (int i = 0; i < 256; i++) {
				executor.execute(blocker);
			}
			boolean rejected = false;
			try {
				executor.execute(blocker);
			} catch (RejectedExecutionException expected) {
				rejected = true;
			}
			check(rejected, "query executor queue must be bounded");
			release.countDown();
		} finally {
			executor.shutdownNow();
		}
	}

	private static void testDownloadProgressFormatting() {
		long oneMegabyte = 1024L * 1024L;
		check(DownloadStatusFormatter.bytesPerSecond(oneMegabyte, 1000L) == oneMegabyte,
				"speed calculation must retain long precision");
		check("1.00 MB/s".equals(DownloadStatusFormatter.speed(oneMegabyte)), "MB/s formatting mismatch");
		check("ETA 01:30".equals(DownloadStatusFormatter.eta(90L * oneMegabyte, oneMegabyte)),
				"ETA formatting mismatch");
		check("ETA --".equals(DownloadStatusFormatter.eta(oneMegabyte, 0L)), "unknown ETA mismatch");
		long fiveGigabytes = 5L * 1024L * 1024L * 1024L;
		check(DownloadStatusFormatter.bytesPerSecond(fiveGigabytes, 1000L) == fiveGigabytes,
				"speed calculation must not truncate values above 2 GB");
	}

	private static void testDownloadTaskLifecycle() {
		DownloadTaskLifecycle lifecycle = new DownloadTaskLifecycle();
		check(lifecycle.snapshot().getState() == DownloadTaskState.PREPARING,
				"new tasks must start in PREPARING");
		lifecycle.markQueued();
		check(lifecycle.snapshot().getState() == DownloadTaskState.QUEUED,
				"prepared tasks must enter QUEUED");

		long firstSubmission = lifecycle.claimInitialSubmission();
		check(firstSubmission != DownloadTaskLifecycle.NO_TOKEN, "initial submission must be claimable once");
		check(lifecycle.claimInitialSubmission() == DownloadTaskLifecycle.NO_TOKEN,
				"initial submission must reject duplicates");
		check(lifecycle.beginExecution(firstSubmission), "claimed initial submission must start");
		check(lifecycle.markDownloading(firstSubmission), "running task must enter DOWNLOADING");

		DownloadTaskLifecycle.RetrySchedule retry = lifecycle.markFailed(firstSubmission, 2, 100L, 3000L,
				"IOException");
		check(retry.shouldRetry(), "first failure must schedule an automatic retry");
		check(retry.getRetryCount() == 1, "retry count must be explicit");
		check(retry.getRetryAtMillis() == 3100L, "retry deadline must include configured delay");
		check(lifecycle.snapshot().getState() == DownloadTaskState.RETRYING,
				"retry delay must remain visible as RETRYING");
		check("IOException".equals(lifecycle.snapshot().getFailureType()),
				"failure display must retain only the error type");
		check(lifecycle.claimScheduledRetry(retry.getToken(), 3099L) == DownloadTaskLifecycle.NO_TOKEN,
				"scheduled retry must not run before its deadline");

		long retrySubmission = lifecycle.claimScheduledRetry(retry.getToken(), 3100L);
		check(retrySubmission != DownloadTaskLifecycle.NO_TOKEN, "scheduled retry must become claimable at deadline");
		check(lifecycle.snapshot().getState() == DownloadTaskState.QUEUED,
				"retry must return to QUEUED after the delay expires");
		check(lifecycle.claimScheduledRetry(retry.getToken(), 3100L) == DownloadTaskLifecycle.NO_TOKEN,
				"scheduled retry token must be single use");
		check(lifecycle.beginExecution(retrySubmission), "claimed retry must start");
		check(lifecycle.markDownloading(retrySubmission), "retry must return to DOWNLOADING");
		lifecycle.markMerging();
		check(lifecycle.snapshot().getState() == DownloadTaskState.MERGING,
				"conversion must be visible as MERGING");
		check(lifecycle.markSucceeded(retrySubmission), "active retry must be able to succeed");
		check(lifecycle.snapshot().getState() == DownloadTaskState.SUCCEEDED,
				"successful task must become terminal");
		lifecycle.pause();
		check(lifecycle.snapshot().getState() == DownloadTaskState.SUCCEEDED,
				"completed tasks must not be paused");
	}

	private static void testPauseCancelAndQueueRejection() {
		DownloadTaskLifecycle lifecycle = new DownloadTaskLifecycle();
		lifecycle.markQueued();
		long staleSubmission = lifecycle.claimInitialSubmission();
		lifecycle.pause();
		check(lifecycle.snapshot().getState() == DownloadTaskState.PAUSED,
				"pause must be visible while a task is queued");
		check(!lifecycle.beginExecution(staleSubmission), "pause must invalidate queued work");

		long manualSubmission = lifecycle.claimManualRetry();
		check(manualSubmission != DownloadTaskLifecycle.NO_TOKEN, "paused task must allow manual continuation");
		check(lifecycle.claimManualRetry() == DownloadTaskLifecycle.NO_TOKEN,
				"concurrent manual continuation must not submit twice");
		check(lifecycle.beginExecution(manualSubmission), "manual continuation must start");
		lifecycle.cancel();
		check(lifecycle.snapshot().getState() == DownloadTaskState.CANCELLED,
				"removed task must become CANCELLED");
		check(!lifecycle.markDownloading(manualSubmission), "cancel must invalidate an active submission");

		DownloadTaskLifecycle rejected = new DownloadTaskLifecycle();
		rejected.markQueued();
		long rejectedToken = rejected.claimInitialSubmission();
		rejected.markSubmissionRejected(rejectedToken);
		check(rejected.snapshot().getState() == DownloadTaskState.FAILED,
				"download queue rejection must become visible failure");
		check("QueueUnavailable".equals(rejected.snapshot().getFailureType()),
				"queue rejection must expose a safe error type");

		DownloadTaskLifecycle exhausted = new DownloadTaskLifecycle();
		exhausted.markQueued();
		long exhaustedToken = exhausted.claimInitialSubmission();
		exhausted.beginExecution(exhaustedToken);
		exhausted.markDownloading(exhaustedToken);
		DownloadTaskLifecycle.RetrySchedule none = exhausted.markFailed(exhaustedToken, 0, 0L, 0L,
				"https://example.invalid/path?credential=placeholder");
		check(!none.shouldRetry(), "zero retry limit must fail immediately");
		check(exhausted.snapshot().getState() == DownloadTaskState.FAILED,
				"exhausted retries must become FAILED");
		check("DownloadFailed".equals(exhausted.snapshot().getFailureType()),
				"failure metadata must reject URLs and arbitrary response text");
	}

	private static void testRetrySchedulerAndStateWiring() throws Exception {
		ScheduledExecutorService scheduler = DownloadExecutors.newRetryScheduler();
		try {
			final CountDownLatch executed = new CountDownLatch(1);
			scheduler.schedule(new Runnable() {
				@Override
				public void run() {
					executed.countDown();
				}
			}, 1L, TimeUnit.MILLISECONDS);
			check(executed.await(5L, TimeUnit.SECONDS), "retry scheduler must execute delayed work");
		} finally {
			scheduler.shutdownNow();
		}

		String monitoringSource = new String(
				Files.readAllBytes(Paths.get("src/nicelee/ui/thread/MonitoringThread.java")), StandardCharsets.UTF_8);
		check(!monitoringSource.contains("panel.continueTask()"),
				"monitoring snapshots must not submit automatic retries");
		check(monitoringSource.contains("DownloadTaskState.RETRYING") || monitoringSource.contains("case RETRYING"),
				"retry countdown must be represented explicitly in the UI");

		String runnableSource = new String(
				Files.readAllBytes(Paths.get("src/nicelee/ui/thread/DownloadRunnableInternal.java")),
				StandardCharsets.UTF_8);
		check(runnableSource.contains("deltaTime > Global.urlValidPeriod"),
				"expired queued URLs must still be resolved again");
		check(runnableSource.contains("submissionToken"),
				"download work must carry a stale-submission guard");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
