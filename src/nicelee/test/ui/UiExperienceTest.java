package nicelee.test.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import nicelee.ui.TabDownload;
import nicelee.ui.util.DownloadStatusFormatter;
import nicelee.ui.util.SwingDispatch;

public class UiExperienceTest {

	public static void main(String[] args) throws Exception {
		testCallAndWaitUsesEdt();
		testRunLaterUsesEdt();
		testExceptionPropagation();
		testPreparingCounterIsThreadSafe();
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
