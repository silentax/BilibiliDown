package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import nicelee.ui.item.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import nicelee.bilibili.INeedLogin;
import nicelee.bilibili.PackageScanLoader;
import nicelee.bilibili.util.CmdUtil;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.RepoUtil;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.SysUtil;
import nicelee.ui.item.MJMenuBar;
import nicelee.ui.thread.BatchDownloadRbyRThread;
import nicelee.ui.thread.CookieRefreshThread;
import nicelee.ui.thread.LoginThread;
import nicelee.ui.thread.MonitoringThread;
import nicelee.ui.util.SwingDispatch;

public class FrameMain extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JTabbedPane jTabbedpane;// 存放选项卡的组件

	public static void main(String[] args) {
		System.out.println();
		if (SysUtil.isMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("apple.awt.application.name", "BilibiliDown");
		}
		// System.getProperties().setProperty("file.encoding", "utf-8");
		boolean isFFmpegSupported = SysUtil.surportFFmpegOfficially();
		System.out.println("Java version:" + System.getProperty("java.specification.version"));
		System.out.println(ResourcesUtil.baseDirectory());
		// 读取配置文件
		ConfigUtil.initConfigs();
		// -v 打印版本，然后退出
		if(args.length == 1 && "-v".equalsIgnoreCase(args[0])) {
			System.out.println(Global.version);
			System.exit(0);
		}
		// 初始化 - 检查对数据文件夹是否有“写”的权限
		InitCheck.checkFileAccess();

		if (Global.lockCheck) {
			if (ConfigUtil.isRunning()) {
				SwingDispatch.runAndWait(new Runnable() {
					@Override
					public void run() {
						JOptionPane.showMessageDialog(null, "程序已经在运行!", "请注意!!", JOptionPane.WARNING_MESSAGE);
					}
				});
				return;
			}
			ConfigUtil.createLock();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				ConfigUtil.deleteLock();
			}));
		}
		
		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				initUITheme();
				FrameMain frame = new FrameMain();
				frame.InitUI();
				frame.setVisible(true);
				frame.setExtendedState(JFrame.NORMAL);
				frame.toFront();
			}
		});

		// 初始化监控线程，用于刷新下载面板
		MonitoringThread th = new MonitoringThread();
		th.start();
		Global.index.showStartupStatus("界面已就绪，正在后台完成启动检查...");
		startBackgroundInitialization(isFFmpegSupported);
	}

	private static void startBackgroundInitialization(final boolean isFFmpegSupported) {
		Thread initialization = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					nicelee.bilibili.util.custom.System.init(Global.syncServerTime);
				} catch (Exception e) {
					e.printStackTrace();
				}

				// 尝试刷新 cookie；等待发生在后台，不再阻塞主窗口显示。
				try {
					INeedLogin inl = new INeedLogin();
					String cookiesStr = inl.readCookies();
					if (cookiesStr != null) {
						Global.needToLogin = true;
						if (Global.tryRefreshCookieOnStartup && !Global.runWASMinBrowser) {
							HttpCookies.setGlobalCookies(HttpCookies.convertCookies(cookiesStr));
							CookieRefreshThread.showTips = false;
							CookieRefreshThread refreshThread = CookieRefreshThread.newInstance();
							refreshThread.start();
							try {
								refreshThread.join();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							} finally {
								CookieRefreshThread.showTips = true;
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				final LoginThread loginThread = new LoginThread();
				loginThread.setName("Thread-StartupLogin");
				loginThread.start();

				try {
					InitCheck.checkFFmpeg(isFFmpegSupported);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (Global.saveToRepo) {
						RepoUtil.init(false);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					PackageScanLoader.validParserClasses.isEmpty();
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (Global.batchDownloadRbyRRunOnStartup) {
					Thread batchStarter = new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								loginThread.join();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								return;
							}
							new BatchDownloadRbyRThread(Global.batchDownloadConfigName).start();
						}
					}, "Thread-StartupBatchDownload");
					batchStarter.setDaemon(true);
					batchStarter.start();
				}
				Global.index.showStartupStatus("启动检查完成，可以开始解析作品");
			}
		}, "Thread-BackgroundInitialization");
		initialization.setDaemon(true);
		initialization.start();
	}

	/**
	 * 
	 */
	static void initUITheme() {
		try {
			if (!Global.themeDefault) {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	public void InitUI() {

		this.setTitle("BiliBili Down~~" + Global.version);
		this.setSize(1200, 760);
		this.setMinimumSize(new Dimension(960, 680));
		this.setResizable(true);
		this.setLocationRelativeTo(null);
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		URL iconURL = this.getClass().getResource("/resources/favicon.png");
		ImageIcon icon = new ImageIcon(iconURL);
		this.setIconImage(icon.getImage());

		// pane 作为内容容器
		JPanel pane = new JPanel(new BorderLayout());
		pane.setBackground(Color.WHITE);
		pane.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
		this.setJMenuBar(new MJMenuBar(this));

		jTabbedpane = new JTabbedPane();
		Global.tabs = jTabbedpane;
		jTabbedpane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		// Index Tab
		Global.index = new TabIndex(jTabbedpane);
		jTabbedpane.addTab("主页", Global.index);
		// 下载页
		Global.downloadTab = new TabDownload();
		jTabbedpane.addTab("下载页", Global.downloadTab);
		// 作品页
//		JLabel label = new JLabel("作品页");
//		TabVideo tab = new TabVideo(label);
//		jTabbedpane.addTab("作品页", tab);
//		jTabbedpane.setTabComponentAt(jTabbedpane.indexOfComponent(tab), label);
//		jTabbedpane.addTab("设置页", new TabSettings(jTabbedpane));
		
		pane.add(jTabbedpane, BorderLayout.CENTER);
		this.setContentPane(pane);
		// 关闭窗口时
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (SysTray.isSysTrayInitiated() && Global.closeToSystray) {
					setExtendedState(JFrame.ICONIFIED);
					setVisible(false);
					return;
				}
				requestApplicationExit();
			}

			@Override
			public void windowIconified(WindowEvent e) {
				if (SysTray.isSysTrayInitiated() && Global.minimizeToSystray) {
					setVisible(false);
				}
			}
		});
//		this.setVisible(true);
		SysTray.buildSysTray(this, icon.getImage());
	}

	public void requestApplicationExit() {
		if (Global.downloadTab != null && Global.downloadTab.activeTask > 0) {
			Object[] options = { "我要退出", "我再想想" };
			String message = String.format("当前仍有 %d 个任务在下载/转码，正在转码的文件退出后可能丢失或异常，确定要退出吗？",
					Global.downloadTab.activeTask);
			int selected = JOptionPane.showOptionDialog(this, message, "警告", JOptionPane.YES_NO_OPTION,
					JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
			if (selected != 0) {
				return;
			}
		}
		CmdUtil.deleteAllInactiveCmdTemp();
		dispose();
		System.exit(0);
	}

}
