package nicelee.ui.thread;

import java.util.concurrent.Callable;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.downloaders.IDownloader;
import nicelee.bilibili.exceptions.BilibiliError;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.CmdUtil;
import nicelee.bilibili.util.RepoUtil;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.custom.System;
import nicelee.ui.Global;
import nicelee.ui.TabDownload;
import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.item.JOptionPaneManager;
import nicelee.ui.util.SwingDispatch;

public class DownloadRunnable implements Runnable {

	private final VideoInfo avInfo;
	private final ClipInfo clip;
	private final String displayName;
	private final String avid;
	private final String cid;
	private final int page;
	private final int qn;
	private String record;
	private volatile DownloadInfoPanel downPanel;
	private volatile boolean promotedToDownloadTask;

	final static String MSG_VIDEO_DOWNLOADED = "您已经下载过视频 %s\n如果想继续下载:\n"
			+ "临时方案: 右上角[配置] -> [下载前先查询记录?] -> [不查询]\n"
			+ "持久化方案: 在配置页搜索并修改配置 bilibili.repo";

	public DownloadRunnable(VideoInfo avInfo, ClipInfo clip, int qn) {
		this.avInfo = avInfo;
		this.displayName = clip.getAvTitle() + "p" + clip.getRemark() + "-" + clip.getTitle();
		this.clip = clip;
		this.avid = clip.getAvId();
		this.cid = String.valueOf(clip.getcId());
		this.page = clip.getPage();
		this.qn = qn;
		this.record = avid + "-" + qn + "-p" + page;
	}

	/** 在任务进入查询队列前立即创建可见的准备卡片。 */
	void prepareUi() {
		if (downPanel != null) {
			return;
		}
		downPanel = SwingDispatch.callAndWait(new Callable<DownloadInfoPanel>() {
			@Override
			public DownloadInfoPanel call() {
				DownloadInfoPanel panel = new DownloadInfoPanel(clip, qn);
				panel.getLbFileName().setText(displayName);
				panel.getLbFileName().setToolTipText(displayName);
				Global.downloadTab.addTaskPanel(panel);
				return panel;
			}
		});
	}

	void cancelPreparation() {
		final DownloadInfoPanel panel = downPanel;
		if (panel == null || promotedToDownloadTask) {
			return;
		}
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				if (!promotedToDownloadTask && panel.getParent() != null) {
					Global.downloadTab.removeTaskPanel(panel);
				}
			}
		});
	}

	@Override
	public void run() {
		try {
			download();
		} catch (BilibiliError e) {
			String errorType = safeErrorType(e);
			JOptionPaneManager.alertErrMsgWithNewThread("下载地址获取失败", failureAdvice(errorType));
			BatchDownloadRbyRThread.taskFail(clip, errorType);
		} catch (Exception e) {
			String errorType = safeErrorType(e);
			BatchDownloadRbyRThread.taskFail(clip, errorType);
			JOptionPaneManager.alertErrMsgWithNewThread("下载地址获取失败", failureAdvice(errorType));
		} finally {
			cancelPreparation();
		}
	}

	private void download() {
		if (TabDownload.isStopAll()) {
			BatchDownloadRbyRThread.taskFail(clip, "stop manually");
			return;
		}
		if (Global.useRepo && RepoUtil.isInRepo(record)) {
			JOptionPaneManager.showMsgWithNewThread("提示", String.format(MSG_VIDEO_DOWNLOADED, record));
			BatchDownloadRbyRThread.taskFail(clip, "already downloaded");
			return;
		}

		final DownloadInfoPanel panel = downPanel;
		if (Global.downloadTaskList.get(panel) != null) {
			BatchDownloadRbyRThread.taskFail(clip, "already in download panel");
			return;
		}

		INeedAV iNeedAV = new INeedAV();
		String urlQuery;
		int realQN;
		if (!ResourcesUtil.isPicture(avid)) {
			urlQuery = iNeedAV.getInputParser(avid).getVideoLink(avid, cid, qn, Global.downloadFormat);
			realQN = iNeedAV.getInputParser(avid).getVideoLinkQN();
		} else {
			urlQuery = clip.getLinks().get(0);
			realQN = 0;
		}

		String formattedTitle = CmdUtil.genFormatedName(avInfo, clip, realQN);
		String avidQn = avid + "-" + realQN;
		this.record = avidQn + "-p" + page;
		if (qn != realQN && Global.useRepo && RepoUtil.isInRepo(record)) {
			JOptionPaneManager.showMsgWithNewThread("提示", String.format(MSG_VIDEO_DOWNLOADED, record));
			BatchDownloadRbyRThread.taskFail(clip, "already downloaded2");
			return;
		}

		final INeedAV resolvedNeedAv = iNeedAV;
		final String resolvedUrl = urlQuery;
		final String resolvedAvidQn = avidQn;
		final String resolvedTitle = formattedTitle;
		final int resolvedQn = realQN;
		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				panel.initDownloadParams(resolvedNeedAv, resolvedUrl, resolvedAvidQn, resolvedTitle, resolvedQn);
			}
		});

		IDownloader existing = Global.downloadTaskList.putIfAbsent(panel, iNeedAV.getDownloader());
		if (existing != null) {
			BatchDownloadRbyRThread.taskFail(clip, "already in download panel2");
			return;
		}
		promotedToDownloadTask = true;
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				panel.markSubmitted();
			}
		});
		BatchDownloadRbyRThread.taskFail(clip, "just put in download panel");

		if (!panel.submitInitialTask(System.currentTimeMillis())) {
			BatchDownloadRbyRThread.taskFail(clip, "QueueUnavailable");
		}
	}

	private static String safeErrorType(Throwable error) {
		String type = error == null ? null : error.getClass().getSimpleName();
		return type == null || !type.matches("[A-Za-z0-9_.-]{1,64}") ? "DownloadPreparationFailed" : type;
	}

	private static String failureAdvice(String errorType) {
		return "错误类型: " + errorType + "\n建议检查网络、登录状态和资源可用性后重试。";
	}
}
