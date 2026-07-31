package nicelee.test.ui;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.ui.TabDownload;
import nicelee.ui.TabVideo;
import nicelee.ui.item.ClipInfoPanel;
import nicelee.ui.item.DownloadInfoPanel;
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
		testBoundedQueryExecutor();
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
