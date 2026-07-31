package nicelee.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.item.JOptionPane;
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
	ImageIcon backgroundIcon = Global.backgroundImg;

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
	public TabDownload() {
		initUI();
	}

	public volatile int activeTask;
	public void refreshStatus(int totalTask, int activeTask, int pauseTask, int doneTask, int queuingTask) {
		lastTotalTask = totalTask;
		lastActiveTask = activeTask;
		lastPauseTask = pauseTask;
		lastDoneTask = doneTask;
		lastQueuingTask = queuingTask;
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
						" 总计: %d / 准备: %d / 下载中: %d / 暂停: %d / 完成: %d / 队列: %d",
						lastTotalTask + preparing, preparing, lastActiveTask, lastPauseTask, lastDoneTask,
						lastQueuingTask);
				if (lbStatus != null && !txt.equals(lbStatus.getText())) {
					lbStatus.setText(txt);
				}
			}
		});
	}

	public void addTaskPanel(DownloadInfoPanel panel) {
		jpContent.add(panel);
		resizeTaskPanel();
	}

	public void removeTaskPanel(DownloadInfoPanel panel) {
		jpContent.remove(panel);
		resizeTaskPanel();
	}

	private void resizeTaskPanel() {
		jpContent.setPreferredSize(new Dimension(1100, Math.max(300, 128 * jpContent.getComponentCount())));
		jpContent.revalidate();
		jpContent.repaint();
	}

	public void initUI() {
//		//占位
//		JLabel lbBlank1 = new JLabel();
//		lbBlank1.setPreferredSize(new Dimension(300, 30));
//		this.add(lbBlank1);

		// 状态 totalTask, activeTask, pauseTask, doneTask, queuingTask
		lbStatus = new JLabel();
		lbStatus.setPreferredSize(new Dimension(500, 30));
		lbStatus.setOpaque(true);
		lbStatus.setBackground(new Color(204, 255, 255));
		lbStatus.setBorder(BorderFactory.createLineBorder(Color.BLUE));
		this.add(lbStatus);
		requestStatusRefresh();

		// 功能按钮
		btnContinue = new MJButton("全部继续");
		btnStop = new MJButton("全部暂停");
		btnDeleteAll = new MJButton("全部删除");
		btnDeleteDown = new MJButton("删除已完成");
		Dimension size = new Dimension(100, 30);
		btnContinue.setPreferredSize(size);
		btnStop.setPreferredSize(size);
		btnDeleteAll.setPreferredSize(size);
		btnDeleteDown.setPreferredSize(size);
		
		btnContinue.addActionListener(this);
		btnStop.addActionListener(this);
		btnDeleteAll.addActionListener(this);
		btnDeleteDown.addActionListener(this);
		this.add(btnContinue);
		this.add(btnStop);
		this.add(btnDeleteAll);
		this.add(btnDeleteDown);

		// 下载任务Panel
		jpContent = new JPanel();
		jpContent.setPreferredSize(new Dimension(1100, 300));
		jpContent.setOpaque(false);

//		DownloadInfoPanel downPan = new DownloadInfoPanel();
//		jpContent.add(downPan);
		jpScorll = new JScrollPane(jpContent);
		// 分别设置水平和垂直滚动条出现方式
		jpScorll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		jpScorll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		// jpScorll.setBorder(BorderFactory.createLineBorder(Color.red));
		jpScorll.setPreferredSize(new Dimension(1150, 620));
		jpScorll.setOpaque(false);
		jpScorll.getViewport().setOpaque(false);
		this.add(jpScorll);
	}

	@Override
	public void paintComponent(Graphics og) {
		if (ui == null || og == null) {
			return;
		}
		// https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/swing002.html#JSTGD472
		Graphics g = og.create();
		Image img = backgroundIcon.getImage();
		int width = img.getWidth(this.getParent());
		int height = img.getHeight(this.getParent());
		int xGap = 5;
		int xCnt = this.getSize().width / (width + xGap) + 1;
		int yGap = 5;
		int yCnt = this.getSize().height / (height + yGap) + 1;
		if( xCnt >= 3) {
			for(int x = 0; x <= xCnt; x++) {
				int xp = xGap + (width + xGap) * x;
				for(int y = 0; y < yCnt; y++) {
					int yp = yGap + (height + yGap) * y;
					g.drawImage(backgroundIcon.getImage(), xp, yp, width, height, this.getParent());
				}
			}
		}else {
			g.drawImage(backgroundIcon.getImage(), 0, 0, this.getSize().width, this.getSize().height, this.getParent());
		}
		this.setOpaque(false);
		try {
            ui.update(g, this);
        } finally {
        	g.dispose();
        }
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
								panel.setFailCnt(0);
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
