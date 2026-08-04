package nicelee.ui.item;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import nicelee.ui.item.JOptionPane;
import nicelee.ui.thread.DownloadRunnableInternal;

import javax.swing.JPanel;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.downloaders.Downloader;
import nicelee.bilibili.downloaders.IDownloader;
import nicelee.bilibili.enums.StatusEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.util.Logger;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.custom.System;
import nicelee.ui.Global;
import nicelee.ui.TabDownload;
import nicelee.ui.task.DownloadTaskLifecycle;
import nicelee.ui.task.DownloadTaskState;
import nicelee.ui.util.AnimeUi;
import nicelee.ui.util.SwingDispatch;

public class DownloadInfoPanel extends JPanel implements ActionListener {

	ClipInfo clipInfo;
	String avTitle; // 原始av标题
	String clipTitle; // 原始clip标题
	String avid;
	String cid;
	int page;
	int remark;
	int qn;
	int realqn;

	// 下载相关
	public volatile INeedAV iNeedAV;
	public String url;
	public String avid_qn;
	public volatile String formattedTitle;
	public volatile boolean stopOnQueue = false;
	private final DownloadTaskLifecycle lifecycle = new DownloadTaskLifecycle();

	volatile long lastCntTime = 0L;
	volatile long lastCnt = 0L;
	/**
	 * 
	 */
	private static final long serialVersionUID = -752743062676819402L;
	String path;
	String fileName;
	long totalSize;
	long currentDown;
	boolean isdownloading = true;

	JButton btnRemove;
	JButton btnOpen;
	JButton btnOpenFolder;
	JButton btnControl;
	JLabel lbCurrentStatus;
	JLabel lbDownFile;
	JLabel lbFileName;
	JLabel lbavName;

	public DownloadInfoPanel(ClipInfo clip, int qn) {
		this.clipInfo = clip;
		this.avTitle = clip.getAvTitle();
		this.clipTitle = clip.getAvTitle();
		this.avid = clip.getAvId();
		this.cid = Long.toString(clip.getcId());
		this.page = clip.getPage();
		this.remark = clip.getRemark();
		this.qn = qn;
		path = "D:\\bilibiliDown\\";
		fileName = "timg.gif";
		totalSize = 0L;
		currentDown = 0L;
		initUI();
	}

	void initUI() {
		this.setLayout(new BorderLayout(10, 4));
		this.setBorder(AnimeUi.cardBorder(8, 12));
		this.setBackground(AnimeUi.SURFACE);
		this.setOpaque(true);
		this.setPreferredSize(new Dimension(900, 76));
		this.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

		// 中间区域：文件名 + 状态 + 副标题 + 进度
		JPanel centerPanel = new JPanel(new BorderLayout(0, 3));
		centerPanel.setOpaque(false);

		lbFileName = new JLabel("尚未生成");
		lbFileName.setFont(lbFileName.getFont().deriveFont(Font.BOLD, 13.0f));
		lbFileName.setForeground(AnimeUi.TEXT_PRIMARY);
		lbCurrentStatus = new JLabel("正在获取下载地址...");
		lbCurrentStatus.setForeground(AnimeUi.TEXT_SECONDARY);

		JPanel topRow = new JPanel(new BorderLayout(8, 0));
		topRow.setOpaque(false);
		topRow.add(lbFileName, BorderLayout.CENTER);
		topRow.add(lbCurrentStatus, BorderLayout.EAST);

		lbavName = new JLabel(avTitle);
		lbavName.setToolTipText(avTitle);
		lbavName.setFont(lbavName.getFont().deriveFont(12.0f));
		lbavName.setForeground(AnimeUi.TEXT_SECONDARY);
		lbDownFile = new JLabel("准备中...");
		lbDownFile.setForeground(AnimeUi.TEXT_SECONDARY);

		JPanel bottomRow = new JPanel(new BorderLayout(8, 0));
		bottomRow.setOpaque(false);
		bottomRow.add(lbavName, BorderLayout.WEST);
		bottomRow.add(lbDownFile, BorderLayout.EAST);

		centerPanel.add(topRow, BorderLayout.NORTH);
		centerPanel.add(bottomRow, BorderLayout.SOUTH);
		this.add(centerPanel, BorderLayout.CENTER);

		// 右侧：操作按钮
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttonPanel.setOpaque(false);

		btnControl = new MJButton("暂停");
		btnControl.addActionListener(this);
		btnControl.setVisible(false);
		AnimeUi.stylePrimaryButton(btnControl);

		btnOpen = new MJButton("打开文件");
		btnOpen.addActionListener(this);
		AnimeUi.styleSecondaryButton(btnOpen);

		btnOpenFolder = new MJButton("打开文件夹");
		btnOpenFolder.addActionListener(this);
		AnimeUi.styleSecondaryButton(btnOpenFolder);

		btnRemove = new MJButton("删除任务");
		btnRemove.addActionListener(this);
		AnimeUi.styleSecondaryButton(btnRemove);

		buttonPanel.add(btnControl);
		buttonPanel.add(btnOpen);
		buttonPanel.add(btnOpenFolder);
		buttonPanel.add(btnRemove);
		this.add(buttonPanel, BorderLayout.EAST);
		setPreparing(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == btnOpenFolder) {
			openPathInBackground(new File(lbFileName.getText()), true);
		} else if (e.getSource() == btnOpen) {
			openPathInBackground(new File(lbFileName.getText()), false);
		} else if (e.getSource() == btnRemove) {
//			if(Global.downloadTaskList.get(this).getStatus() == 0) {
//				JOptionPane.showMessageDialog(this, "当前正在文件下载中!", "警告", JOptionPane.WARNING_MESSAGE);
//			}
				if(TabDownload.isStopAll()) {
					Logger.println("停止任务中，请误操作");
					return;
				}
				Object[] options = { "移除任务", "取消" };
				int selected = JOptionPane.showOptionDialog(this,
						"将从列表移除此任务，并删除未完成的 .part 临时文件。\n已完成的文件不会被删除。",
						"确认移除任务", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
						null, options, options[1]);
				if (selected == 0)
					removeTask(true);
		} else if (e.getSource() == btnControl) {
			if(TabDownload.isStopAll()) {
				Logger.println("停止任务中，请误操作");
				return;
			}
			if (iNeedAV == null) {
				return;
			}
			btnControl.setEnabled(false);
			Thread controlThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						DownloadTaskState state = lifecycle.snapshot().getState();
						if (state == DownloadTaskState.QUEUED || state == DownloadTaskState.DOWNLOADING
								|| state == DownloadTaskState.MERGING) {
							stopTask();
						} else {
							continueTask();
						}
					} catch (RuntimeException error) {
						JOptionPaneManager.alertErrMsgWithNewThread("任务操作失败",
								error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
					} finally {
						SwingDispatch.runLater(new Runnable() {
							@Override
							public void run() {
								btnControl.setEnabled(true);
							}
						});
					}
				}
			}, "Thread-ControlDownload");
			controlThread.setDaemon(true);
			controlThread.start();
		}
	}

	/**
	 * 下载前的初始化工作
	 */
	public void initDownloadParams(INeedAV iNeedAV, String url, String avid_qn, String formattedTitle, int realqn) {
		this.iNeedAV = iNeedAV;
		this.avid_qn = avid_qn;
		this.formattedTitle = formattedTitle;
		this.url = url;
		this.realqn = realqn;
		this.lbavName.setText(formattedTitle);
		this.lbavName.setToolTipText(formattedTitle);
		this.stopOnQueue = false;
		this.lifecycle.markQueued();
		this.lbCurrentStatus.setText("下载地址已就绪，等待调度...");
		this.lbDownFile.setText("等待下载中...");
	}

	public void markSubmitted() {
		setPreparing(false);
	}

	public boolean submitInitialTask(long urlTimestamp) {
		long token = lifecycle.claimInitialSubmission();
		return submitExecution(token, urlTimestamp, false, 0);
	}

	public void setPreparing(boolean preparing) {
		btnOpen.setEnabled(!preparing);
		btnOpenFolder.setEnabled(!preparing);
		btnRemove.setEnabled(!preparing);
		if (preparing) {
			btnControl.setVisible(false);
			lbCurrentStatus.setText("正在获取下载地址...");
			lbDownFile.setText("准备中...");
		}
	}

	private void openPathInBackground(final File file, final boolean openFolder) {
		Thread openThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (openFolder && System.getProperty("os.name").toLowerCase().startsWith("win") && file.exists()) {
						String[] command = { "explorer", "/e,/select,", file.getAbsolutePath() };
						Runtime.getRuntime().exec(command);
						return;
					}
					if (!Desktop.isDesktopSupported()) {
						throw new IllegalStateException("当前系统不支持 Desktop 打开操作");
					}
					File target = openFolder ? file.getParentFile() : file;
					if (target == null || !target.exists()) {
						throw new IllegalStateException("目标路径不存在");
					}
					Desktop.getDesktop().open(target);
				} catch (Exception error) {
					JOptionPaneManager.alertErrMsgWithNewThread(openFolder ? "打开文件夹失败" : "打开文件失败",
							error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
				}
			}
		}, openFolder ? "Thread-OpenDownloadFolder" : "Thread-OpenDownloadFile");
		openThread.setDaemon(true);
		openThread.start();
	}

	/**
	 * 停止任务(方法内包含状态判断)
	 */
	public void stopTask() {
		if (iNeedAV == null) {
			return;
		}
		lifecycle.pause();
		stopOnQueue = true;
		Downloader downloader = iNeedAV.getDownloader();
		downloader.stopTask();
	}

	/**
	 * 继续任务(方法内包含状态判断)
	 */
	public boolean continueTask() {
		if (iNeedAV == null) {
			return false;
		}
		stopOnQueue = false;
		long token = lifecycle.claimManualRetry();
		return submitExecution(token, System.currentTimeMillis(), true, 0);
	}

	public DownloadTaskLifecycle.Snapshot getTaskSnapshot() {
		return lifecycle.snapshot();
	}

	public boolean beginExecution(long submissionToken) {
		return lifecycle.beginExecution(submissionToken);
	}

	public boolean markDownloading(long submissionToken) {
		return lifecycle.markDownloading(submissionToken);
	}

	public void markMerging() {
		lifecycle.markMerging();
	}

	public boolean markExecutionSucceeded(long submissionToken) {
		return lifecycle.markSucceeded(submissionToken);
	}

	public void markExecutionFailed(long submissionToken, String failureType) {
		final DownloadTaskLifecycle.RetrySchedule retry = lifecycle.markFailed(submissionToken,
				Global.maxFailRetry, System.currentTimeMillis(), Global.downloadRetryDelay, failureType);
		if (!retry.shouldRetry()) {
			return;
		}
		ScheduledExecutorService scheduler = Global.downloadRetryScheduler;
		if (scheduler == null || scheduler.isShutdown()) {
			lifecycle.markRetrySchedulingRejected(retry.getToken());
			return;
		}
		long delay = Math.max(0L, retry.getRetryAtMillis() - System.currentTimeMillis());
		try {
			scheduler.schedule(new Runnable() {
				@Override
				public void run() {
					long submissionToken = lifecycle.claimScheduledRetry(retry.getToken(),
							System.currentTimeMillis());
					submitExecution(submissionToken, System.currentTimeMillis(), true, retry.getRetryCount());
				}
			}, delay, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException e) {
			lifecycle.markRetrySchedulingRejected(retry.getToken());
		}
	}

	private boolean submitExecution(long submissionToken, long urlTimestamp, boolean retry, int retryCount) {
		if (submissionToken == DownloadTaskLifecycle.NO_TOKEN) {
			return false;
		}
		try {
			if (Global.downLoadThreadPool == null || Global.downLoadThreadPool.isShutdown()) {
				throw new RejectedExecutionException("下载队列已停止");
			}
			Global.downLoadThreadPool.execute(
					new DownloadRunnableInternal(this, urlTimestamp, retry, retryCount, submissionToken));
			return true;
		} catch (RejectedExecutionException e) {
			lifecycle.markSubmissionRejected(submissionToken);
			Logger.println("下载任务提交失败: QueueUnavailable");
			return false;
		}
	}

	/**
	 * 删除任务
	 */
	public void removeTask(boolean deleteAll) {
		if (iNeedAV == null) {
			return;
		}
		final IDownloader downloader = Global.downloadTaskList.get(this);
		if (downloader == null) {
			return;
		}
		// 删除所有 或 删除已完成的任务
		// 0 正在下载; 1 下载完毕; -1 出现错误; -2 人工停止;-3队列中
		if (deleteAll || downloader.currentStatus() == StatusEnum.SUCCESS) {
			this.lifecycle.cancel();
			this.stopOnQueue = true;
			// 全局监控撤销
			Global.downloadTaskList.remove(this, downloader);
			// 当前页面控件删除
			Global.downloadTab.removeTaskPanel(this);
			final File downloadFile = downloader.file();
			Thread cleanupThread = new Thread(new Runnable() {
				@Override
				public void run() {
					downloader.stopTask();
					if (!ResourcesUtil.deleteDownloadPartFiles(downloadFile)) {
						Logger.println("未能删除下载任务的临时文件");
					}
				}
			}, "Thread-RemoveDownloadTask");
			cleanupThread.setDaemon(true);
			cleanupThread.start();
		}
	}

	public JLabel getLbCurrentStatus() {
		return lbCurrentStatus;
	}

	public void setLbCurrentStatus(JLabel lbCurrentStatus) {
		this.lbCurrentStatus = lbCurrentStatus;
	}

	public JLabel getLbDownFile() {
		return lbDownFile;
	}

	public void setLbDownFile(JLabel lbDownFile) {
		this.lbDownFile = lbDownFile;
	}

	public JLabel getLbFileName() {
		return lbFileName;
	}

	public void setLbFileName(JLabel lbFileName) {
		this.lbFileName = lbFileName;
	}

	public JButton getBtnControl() {
		return btnControl;
	}

	public void setBtnControl(JButton btnControl) {
		this.btnControl = btnControl;
	}

	@Override
	public int hashCode() {
		return (avid + page).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		// System.out.println("DownloadInfoPanel - equals:");
		if (obj instanceof DownloadInfoPanel) {
			DownloadInfoPanel down = (DownloadInfoPanel) obj;
			return (avid.equals(down.avid) && page == down.page);
		}
		return false;
	}

	public long getLastCntTime() {
		return lastCntTime;
	}

	public void setLastCntTime(long lastCntTime) {
		this.lastCntTime = lastCntTime;
	}

	public long getLastCnt() {
		return lastCnt;
	}

	public void setLastCnt(long lastCnt) {
		this.lastCnt = lastCnt;
	}

	public String getAvid() {
		return avid;
	}

	public void setAvid(String avid) {
		this.avid = avid;
	}

	public String getCid() {
		return cid;
	}

	public ClipInfo getClipInfo() {
		return clipInfo;
	}

	public int getQn() {
		return qn;
	}

	public int getRealqn() {
		return realqn;
	}

	public void setRealqn(int realqn) {
		this.realqn = realqn;
	}

}
