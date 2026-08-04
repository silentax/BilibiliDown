package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.item.JOptionPane;
import nicelee.ui.util.AnimeUi;
import nicelee.ui.item.MJButton;
import nicelee.ui.thread.DownloadExecutors;
import nicelee.ui.util.SwingDispatch;
import nicelee.bilibili.util.Logger;

public class TabDownload extends JPanel implements ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8714599826187286737L;
	private static volatile boolean stopAll = false;
	JPanel jpContent;
	JScrollPane jpScorll;
	JLabel lbStatus;
	JButton btnContinue, btnStop, btnDeleteAll, btnDeleteDown;
	private final AtomicInteger preparingTaskCount = new AtomicInteger();
	private volatile int lastTotalTask;
	private volatile int lastActiveTask;
	private volatile int lastPauseTask;
	private volatile int lastDoneTask;
	private volatile int lastQueuingTask;
	private volatile int lastRetryingTask;
	public TabDownload() {
		initUI();
	}

	public volatile int activeTask;
	public void refreshStatus(int totalTask, int activeTask, int pauseTask, int doneTask, int queuingTask,
			int retryingTask) {
		lastTotalTask = totalTask;
		lastActiveTask = activeTask;
		lastPauseTask = pauseTask;
		lastDoneTask = doneTask;
		lastQueuingTask = queuingTask;
		lastRetryingTask = retryingTask;
		requestStatusRefresh();
	}

	public void beginPreparingTask() {
		preparingTaskCount.incrementAndGet();
		requestStatusRefresh();
	}

	public void finishPreparingTask() {
		preparingTaskCount.updateAndGet(value -> value > 0 ? value - 1 : 0);
		requestStatusRefresh();
	}

	public int getPreparingTaskCount() {
		return preparingTaskCount.get();
	}

	private void requestStatusRefresh() {
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				int preparing = preparingTaskCount.get();
				activeTask = lastActiveTask;
				String txt = String.format(
						" 总计: %d / 准备: %d / 下载中: %d / 重试: %d / 暂停: %d / 完成: %d / 队列: %d",
						lastTotalTask + preparing, preparing, lastActiveTask, lastRetryingTask, lastPauseTask,
						lastDoneTask, lastQueuingTask);
				if (lbStatus != null && !txt.equals(lbStatus.getText())) {
					lbStatus.setText(txt);
				}
			}
		});
	}

	public void addTaskPanel(DownloadInfoPanel panel) {
		jpContent.add(panel);
		jpContent.add(Box.createVerticalStrut(8));
		resizeTaskPanel();
	}

	public void removeTaskPanel(DownloadInfoPanel panel) {
		for (int i = 0; i < jpContent.getComponentCount(); i++) {
			if (jpContent.getComponent(i) == panel) {
				jpContent.remove(i);
				if (i < jpContent.getComponentCount()) {
					jpContent.remove(i);
				}
				break;
			}
		}
		resizeTaskPanel();
	}

	private void resizeTaskPanel() {
		int taskCount = 0;
		for (java.awt.Component c : jpContent.getComponents()) {
			if (c instanceof DownloadInfoPanel) {
				taskCount++;
			}
		}
		int height = taskCount > 0 ? 84 * taskCount : 300;
		jpContent.setPreferredSize(new Dimension(0, Math.max(300, height)));
		jpContent.revalidate();
		jpContent.repaint();
	}

	public void initUI() {
		this.setLayout(new BorderLayout(0, 8));
		JPanel toolbar = new JPanel(new BorderLayout(12, 0));
		toolbar.setOpaque(false);
		toolbar.setBorder(new EmptyBorder(8, 8, 0, 8));

		// 状态 totalTask, activeTask, pauseTask, doneTask, queuingTask
		lbStatus = new JLabel();
		lbStatus.setOpaque(true);
		lbStatus.setBackground(AnimeUi.ACCENT_SOFT);
		lbStatus.setForeground(AnimeUi.TEXT_PRIMARY);
		lbStatus.setBorder(AnimeUi.cardBorder(6, 10));
		toolbar.add(lbStatus, BorderLayout.CENTER);
		requestStatusRefresh();

		// 功能按钮
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		btnContinue = new MJButton("全部继续");
		AnimeUi.styleSecondaryButton(btnContinue);
		btnStop = new MJButton("全部暂停");
		AnimeUi.styleSecondaryButton(btnStop);
		btnDeleteAll = new MJButton("全部删除");
		AnimeUi.styleSecondaryButton(btnDeleteAll);
		btnDeleteDown = new MJButton("删除已完成");
		AnimeUi.styleSecondaryButton(btnDeleteDown);
		Dimension size = new Dimension(100, 30);
		btnContinue.setPreferredSize(size);
		btnStop.setPreferredSize(size);
		btnDeleteAll.setPreferredSize(size);
		btnDeleteDown.setPreferredSize(size);
		
		btnContinue.addActionListener(this);
		btnStop.addActionListener(this);
		btnDeleteAll.addActionListener(this);
		btnDeleteDown.addActionListener(this);
		actions.add(btnContinue);
		actions.add(btnStop);
		actions.add(btnDeleteAll);
		actions.add(btnDeleteDown);
		toolbar.add(actions, BorderLayout.EAST);
		this.add(toolbar, BorderLayout.NORTH);

		// 下载任务Panel
		jpContent = new JPanel();
		jpContent.setLayout(new javax.swing.BoxLayout(jpContent, javax.swing.BoxLayout.Y_AXIS));
		jpContent.setBorder(new EmptyBorder(8, 8, 8, 8));
		jpContent.setPreferredSize(new Dimension(0, 300));
		jpContent.setOpaque(false);

//		DownloadInfoPanel downPan = new DownloadInfoPanel();
//		jpContent.add(downPan);
		jpScorll = new JScrollPane(jpContent);
		// 分别设置水平和垂直滚动条出现方式
		jpScorll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		jpScorll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		// jpScorll.setBorder(BorderFactory.createLineBorder(Color.red));
		jpScorll.setOpaque(false);
		jpScorll.getViewport().setOpaque(false);
		this.add(jpScorll, BorderLayout.CENTER);
	}

	@Override
	public void paintComponent(Graphics og) {
		super.paintComponent(og);
		if (og == null) {
			return;
		}
		AnimeUi.paintBackground((Graphics2D) og, getWidth(), getHeight());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == btnContinue) {
			stopAll = false;
			btnContinue.setEnabled(false);
			Thread continueThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						for (DownloadInfoPanel panel : Global.downloadTaskList.keySet()) {
							try {
								panel.continueTask();
							} catch (RuntimeException error) {
								Logger.println("继续下载任务失败: " + error.getClass().getSimpleName());
							}
						}
					} finally {
						SwingDispatch.runLater(new Runnable() {
							@Override
							public void run() {
								btnContinue.setEnabled(true);
							}
						});
					}
				}
			}, "Thread-ContinueAllDownloads");
			continueThread.setDaemon(true);
			continueThread.start();
		} else if (e.getSource() == btnStop) {
			// 约3s后置false
			stopAll = true;
			btnContinue.setEnabled(false);
			btnStop.setEnabled(false);
			btnDeleteAll.setEnabled(false);
			// 停止进程可能涉及 I/O，放到后台，避免阻塞 EDT。
			Thread stopThread = new Thread(new Runnable() {
				@Override
				public void run() {
					Global.downLoadThreadPool.shutdownNow();
					for(DownloadInfoPanel dp : Global.downloadTaskList.keySet()) {
						dp.stopTask();
					}
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					//双保险
					for(DownloadInfoPanel dp : Global.downloadTaskList.keySet()) {
						dp.stopTask();
					}
					int fixPool = Global.downloadPoolSize;
					Global.downLoadThreadPool = DownloadExecutors.newPriorityFixedThreadPool(fixPool);
					stopAll = false;
					SwingDispatch.runLater(new Runnable() {
						@Override
						public void run() {
							btnContinue.setEnabled(true);
							btnStop.setEnabled(true);
							btnDeleteAll.setEnabled(true);
						}
					});
				}
			});
			stopThread.setName("Thread-StopAllDownloads");
			stopThread.setDaemon(true);
			stopThread.start();
			} else if (e.getSource() == btnDeleteAll) {
				if (!Global.downloadTaskList.isEmpty()) {
					Object[] options = { "移除全部", "取消" };
					int selected = JOptionPane.showOptionDialog(this,
							"将移除全部下载任务，并删除未完成的 .part 临时文件。\n已完成的文件不会被删除。",
							"确认移除全部任务", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
							null, options, options[1]);
					if (selected == 0) {
						for(DownloadInfoPanel dp : Global.downloadTaskList.keySet()) {
							dp.removeTask(true);
						}
					}
				}
		} else if (e.getSource() == btnDeleteDown) {
			for(DownloadInfoPanel dp : Global.downloadTaskList.keySet()) {
				dp.removeTask(false);
			}
		}
	}

	public JPanel getJpContent() {
		return jpContent;
	}

	public void setJpContent(JPanel jpContent) {
		this.jpContent = jpContent;
	}

	public static boolean isStopAll() {
		return stopAll;
	}

}
