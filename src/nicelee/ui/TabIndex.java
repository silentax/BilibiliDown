package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import nicelee.bilibili.enums.DownloadModeEnum;
import nicelee.bilibili.model.FavList;
import nicelee.bilibili.util.Logger;
import nicelee.ui.item.MJButton;
import nicelee.ui.item.MJTabVideo;
import nicelee.ui.item.MJTextField;
import nicelee.ui.thread.GetVideoDetailThread;
import nicelee.ui.thread.LoginThread;
import nicelee.ui.util.AnimeUi;
import nicelee.ui.util.SwingDispatch;

public class TabIndex extends JPanel implements ActionListener, MouseListener, ItemListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5829023045158490349L;
	public ImageIcon imgIconHeaderDefault = new ImageIcon(this.getClass().getResource("/resources/header.png"));
	public JLabel jlHeader;
	public JComboBox<Object> cmbFavList=new JComboBox<>();
	JComboBox<String> cmbDownloadMode = new JComboBox<>(new String[] { "视频+音频", "仅视频", "仅音频" });
	String placeHolder = "请在此输入B站 BV/av/ep/ss/md/ml号或地址";
	JTextField txtSearch = new MJTextField(placeHolder);
	//new MJTextField("https://www.bilibili.com/video/av35296336");
	JButton btnSearch = new MJButton("查找");
	JButton btnSearchNextPage = new MJButton("下一页");
	JLabel lbSearchStatus = new JLabel("就绪");
	private boolean searchInProgress;
	
	JTextArea consoleArea = new JTextArea(20, 50);
	JTabbedPane jTabbedpane;

	public TabIndex(JTabbedPane jTabbedpane) {
		this.jTabbedpane = jTabbedpane;
		init();
	}

	public void init() {
		this.setLayout(new BorderLayout(0, 20));
		this.setBorder(new EmptyBorder(28, 34, 34, 34));

		JPanel headerPanel = new JPanel(new BorderLayout(22, 0));
		headerPanel.setOpaque(true);
		headerPanel.setBackground(new Color(255, 255, 255, 238));
		headerPanel.setBorder(AnimeUi.cardBorder(20, 24));
		imgIconHeaderDefault = new ImageIcon(imgIconHeaderDefault.getImage().getScaledInstance(68, 68, Image.SCALE_SMOOTH));
		jlHeader = new JLabel(imgIconHeaderDefault);
		jlHeader.setToolTipText("登录或查看账号状态");
		jlHeader.addMouseListener(this);
		headerPanel.add(jlHeader, BorderLayout.EAST);

		JPanel titlePanel = new JPanel(new BorderLayout(0, 7));
		titlePanel.setOpaque(false);
		JLabel titleLabel = new JLabel("BilibiliDown");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 30.0f));
		titleLabel.setForeground(AnimeUi.TEXT_PRIMARY);
		JLabel subtitle = new JLabel("把喜欢的内容，清爽地收进本地");
		subtitle.setFont(subtitle.getFont().deriveFont(15.0f));
		subtitle.setForeground(AnimeUi.TEXT_SECONDARY);
		JLabel featureLine = new JLabel("粘贴链接  ·  选择分集  ·  安全下载");
		featureLine.setForeground(AnimeUi.ACCENT);
		titlePanel.add(titleLabel, BorderLayout.NORTH);
		titlePanel.add(subtitle, BorderLayout.CENTER);
		titlePanel.add(featureLine, BorderLayout.SOUTH);
		headerPanel.add(titlePanel, BorderLayout.CENTER);

		JPanel searchPanel = new JPanel(new BorderLayout(0, 12));
		searchPanel.setOpaque(true);
		searchPanel.setBackground(AnimeUi.SURFACE);
		searchPanel.setBorder(AnimeUi.cardBorder(18, 20));
		JPanel searchControls = new JPanel(new BorderLayout(10, 12));
		searchControls.setOpaque(false);

		txtSearch.setPreferredSize(new Dimension(520, 42));
		txtSearch.setMinimumSize(new Dimension(220, 40));
		AnimeUi.styleInput(txtSearch);
		txtSearch.addActionListener(this);
		btnSearch.addActionListener(this);
		AnimeUi.stylePrimaryButton(btnSearch);
		btnSearch.setPreferredSize(new Dimension(96, 42));
		btnSearchNextPage.addActionListener(this);
		AnimeUi.styleSecondaryButton(btnSearchNextPage);
		
        cmbFavList.addItem("---我的收藏夹---");
		cmbFavList.setPreferredSize(new Dimension(170, 34));
		cmbFavList.addItemListener(this);
		cmbDownloadMode.setPreferredSize(new Dimension(130, 34));
		cmbDownloadMode.setSelectedIndex(Global.downloadMode.getMode());
		cmbDownloadMode.addItemListener(this);

		JPanel inputRow = new JPanel(new BorderLayout(10, 0));
		inputRow.setOpaque(false);
		inputRow.add(txtSearch, BorderLayout.CENTER);
		inputRow.add(btnSearch, BorderLayout.EAST);
		searchControls.add(inputRow, BorderLayout.NORTH);

		JPanel options = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		options.setOpaque(false);
		JLabel modeLabel = new JLabel("下载内容");
		modeLabel.setForeground(AnimeUi.TEXT_SECONDARY);
		options.add(modeLabel);
		options.add(cmbDownloadMode);
		options.add(cmbFavList);
		options.add(btnSearchNextPage);
		searchControls.add(options, BorderLayout.CENTER);

		lbSearchStatus.setForeground(AnimeUi.TEXT_SECONDARY);
		lbSearchStatus.setBorder(new EmptyBorder(2, 4, 2, 4));
		searchPanel.add(searchControls, BorderLayout.CENTER);
		searchPanel.add(lbSearchStatus, BorderLayout.SOUTH);
		JPanel topPanel = new JPanel(new BorderLayout(0, 24));
		topPanel.setOpaque(false);
		topPanel.add(headerPanel, BorderLayout.NORTH);
		topPanel.add(searchPanel, BorderLayout.CENTER);
		this.add(topPanel, BorderLayout.NORTH);
		this.setOpaque(false);
	}
	
	/**
	 * 关闭所有视频Tab
	 */
	public void closeAllVideoTabs() {
		System.out.println("当前Tab数量： " + (jTabbedpane.getTabCount() - 2));
		System.out.println("正在关闭Tab标签页");
		for(int i = jTabbedpane.getTabCount() - 1; i >= 2 ; i--) {
			jTabbedpane.removeTabAt(i);
		}
		System.out.println("当前Tab数量： " + (jTabbedpane.getTabCount() - 2));
	}
	
	/**
	 * 根据需要下载所有打开的Tab页视频
	 * @param downAll
	 * @param qn
	 */
	public void downVideoTabs(boolean downAll, int qn) {
		for(int i = 0; i < jTabbedpane.getTabCount(); i++) {
			//判断是否为Video标签页, 是就下载
			System.out.printf("Tab 页共 %d 个，当前第 %d 个\r\n",
					jTabbedpane.getTabCount(),
					i);
			Component comp = jTabbedpane.getComponentAt(i);
			if(comp instanceof TabVideo ) {
				TabVideo tabVideo = (TabVideo) comp;
				tabVideo.download(downAll, qn);
			}
		}
	}
	@Override
	public void paintComponent(Graphics og) {
		super.paintComponent(og);
		if (og == null) {
			return;
		}
		AnimeUi.paintBackground((Graphics2D) og, getWidth(), getHeight());
	}
	
	/**
	 * 对应 查找 按钮的点击事件
	 */
	static Pattern paramPattern = Pattern.compile("(.*)p=([0-9]+)$");
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == btnSearch || e.getSource() == txtSearch) {
			search();
		}else if(e.getSource() == btnSearchNextPage){
			String origin = txtSearch.getText();
			int page = 1;
			String modified = null;
			Matcher matcher = paramPattern.matcher(origin);
			if(matcher.find()) {
				page = Integer.parseInt(matcher.group(2));
				modified = matcher.group(1) + "p=" + (page+1);
			}else {
				modified = origin + " p=" + (page+1);
			}
			txtSearch.setText(modified);
			search();
		}
	}

	/**
	 * 根据输入查找 av信息，并弹出av信息的Tab页
	 */
	public void search() {
		if (searchInProgress) {
			lbSearchStatus.setText("已有解析任务正在进行，请稍候...");
			return;
		}
		String searchContent = txtSearch.getText();
		if (searchContent == null || searchContent.trim().isEmpty() || placeHolder.equals(searchContent)) {
			lbSearchStatus.setText("请输入 B 站作品编号或链接");
			return;
		}
		setSearchBusy(true, "正在解析：" + compact(searchContent));
		popVideoInfoTab(searchContent.trim());
	}

	/**
	 * 弹出avId对应的Video 标签页
	 * @param avId
	 */
	private void popVideoInfoTab(String searchContent) {
		// 作品页
		JLabel label = new JLabel("正在加载中...");
//		final TabVideo tab = new TabVideo(label);
		final TabVideo tab = new MJTabVideo(jTabbedpane, label, searchContent);
		tab.setLoading(true);
		jTabbedpane.addTab("作品页", tab);
		jTabbedpane.setTabComponentAt(jTabbedpane.indexOfComponent(tab), label);
		jTabbedpane.setSelectedComponent(tab);
		GetVideoDetailThread th = new GetVideoDetailThread(tab, searchContent, new GetVideoDetailThread.Listener() {
			@Override
			public void onFinished(boolean success, String message) {
				setSearchBusy(false, message);
			}
		});
		th.start();
	}

	private void setSearchBusy(boolean busy, String status) {
		searchInProgress = busy;
		btnSearch.setEnabled(!busy);
		btnSearchNextPage.setEnabled(!busy);
		cmbFavList.setEnabled(!busy);
		lbSearchStatus.setText(status);
		lbSearchStatus.setForeground(busy ? AnimeUi.ACCENT : AnimeUi.TEXT_SECONDARY);
	}

	private String compact(String value) {
		String text = value.trim().replace('\n', ' ').replace('\r', ' ');
		return text.length() > 60 ? text.substring(0, 57) + "..." : text;
	}

	public void showStartupStatus(final String status) {
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				if (!searchInProgress) {
					lbSearchStatus.setText(status);
				}
			}
		});
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if(e.getSource() == jlHeader) {
			Global.needToLogin = true;
			LoginThread loginTh = new LoginThread();
			loginTh.start();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if(e.getSource() == jlHeader) {
			jlHeader.setBorder(BorderFactory.createLineBorder(Color.red));
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if(e.getSource() == jlHeader) {
			jlHeader.setBorder(null);
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
		
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			if (e.getSource() == cmbDownloadMode) {
				Global.downloadMode = DownloadModeEnum.getModeEnum(cmbDownloadMode.getSelectedIndex());
				Logger.println("下载模式已切换为: " + cmbDownloadMode.getSelectedItem());
				return;
			}
			if(e.getItem() instanceof FavList) {
            	FavList fav = (FavList) e.getItem();
            	String url = "https://space.bilibili.com/%s/favlist?fid=%s&ftype=create";
            	url = String.format(url, fav.getOwnerId(), fav.getfId());
            	txtSearch.setText(url);
            	txtSearch.setForeground(Color.BLACK);
    			search();
            }else if("稍后再看".equals(e.getItem().toString())){
            	txtSearch.setText("https://www.bilibili.com/watchlater/#/list");
            	txtSearch.setForeground(Color.BLACK);
    			search();
            }
            
        }
	}
}
