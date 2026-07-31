package nicelee.ui.thread;

import java.util.concurrent.RejectedExecutionException;

import nicelee.ui.Global;
import nicelee.ui.TabDownload;
import nicelee.ui.item.JOptionPaneManager;

/**
 * 下载地址查询任务的统一入口，负责准备阶段反馈和线程池拒绝处理。
 */
public final class DownloadTaskDispatcher {

	private DownloadTaskDispatcher() {
	}

	public static void submit(final DownloadRunnable task) {
		final TabDownload downloadTab = Global.downloadTab;
		if (downloadTab == null) {
			JOptionPaneManager.alertErrMsgWithNewThread("无法创建下载任务", "下载页面尚未初始化，请稍后重试。");
			return;
		}

		downloadTab.beginPreparingTask();
		try {
			task.prepareUi();
			Global.queryThreadPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						task.run();
					} finally {
						downloadTab.finishPreparingTask();
					}
					if (Global.sleepAfterDownloadQuery > 0) {
						try {
							Thread.sleep(Global.sleepAfterDownloadQuery);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				}
			});
		} catch (RejectedExecutionException e) {
			task.cancelPreparation();
			downloadTab.finishPreparingTask();
			JOptionPaneManager.alertErrMsgWithNewThread("下载任务未提交", "下载查询队列暂不可用，请稍后重试。");
		} catch (RuntimeException e) {
			task.cancelPreparation();
			downloadTab.finishPreparingTask();
			throw e;
		}
	}
}
