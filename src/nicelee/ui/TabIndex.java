package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URL;
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
import nicelee.ui.util.SwingDispatch;

public class TabIndex extends JPanel implements ActionListener, MouseListener, ItemListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5829023045158490349L;
	public ImageIcon imgIconHeaderDefault = new ImageIcon(this.getClass().getResource("/resources/header.png"));
	public ImageIcon backgroundIcon = Global.backgroundImg;
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
		this.setLayout(new BorderLayout(0, 24));
		this.setBorder(new EmptyBorder(36, 36, 36, 36));

		JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
		headerPanel.setOpaque(false);
		imgIconHeaderDefault = new ImageIcon(imgIconHeaderDefault.getImage().getScaledInstance(80, 80, Image.SCALE_DEFAULT));
		jlHeader = new JLabel(imgIconHeaderDefault);
		jlHeader.addMouseListener(this);
		headerPanel.add(jlHeader, BorderLayout.EAST);

		URL fileURL = this.getClass().getResource("/resources/title.png");
		ImageIcon imgIcon = new ImageIcon(fileURL);
		JLabel titleLabel = new JLabel(imgIcon, JLabel.CENTER);
		headerPanel.add(titleLabel, BorderLayout.CENTER);

		JPanel searchPanel = new JPanel(new BorderLayout(0, 10));
		searchPanel.setOpaque(false);
		JPanel searchControls = new JPanel(new GridBagLayout());
		searchControls.setOpaque(false);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 4, 0, 4);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		txtSearch.setPreferredSize(new Dimension(420, 40));
		txtSearch.setMinimumSize(new Dimension(220, 40));
		txtSearch.addActionListener(this);
		btnSearch.addActionListener(this);
		btnSearch.setPreferredSize(new Dimension(80, 40));
		btnSearchNextPage.addActionListener(this);
		btnSearchNextPage.setPreferredSize(new Dimension(80, 40));
		
        cmbFavList.addItem("---我的收藏夹---");
		cmbFavList.setPreferredSize(new Dimension(120, 40));
		cmbFavList.addItemListener(this);
		cmbDownloadMode.setPreferredSize(new Dimension(110, 40));
		cmbDownloadMode.setSelectedIndex(Global.downloadMode.getMode());
		cmbDownloadMode.addItemListener(this);

		constraints.gridx = 0;
		constraints.weightx = 1.0;
		searchControls.add(txtSearch, constraints);
		constraints.weightx = 0.0;
		constraints.gridx++;
		searchControls.add(cmbDownloadMode, constraints);
		constraints.gridx++;
		searchControls.add(btnSearch, constraints);
		constraints.gridx++;
		searchControls.add(btnSearchNextPage, constraints);
		constraints.gridx++;
		searchControls.add(cmbFavList, constraints);

		lbSearchStatus.setForeground(new Color(60, 60, 60));
		lbSearchStatus.setBorder(new EmptyBorder(2, 4, 2, 4));
		searchPanel.add(searchControls, BorderLayout.NORTH);
		searchPanel.add(lbSearchStatus, BorderLayout.CENTER);
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
		if (og == null || backgroundIcon == null) {
			return;
		}
		Graphics g = og.create();
		Image img = backgroundIcon.getImage();
		int width = img.getWidth(this.getParent());
		int height = img.getHeight(this.getParent());
		if (width <= 0 || height <= 0) {
			g.dispose();
			return;
		}
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
		g.dispose();
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
		lbSearchStatus.setForeground(busy ? new Color(25, 90, 160) : new Color(60, 60, 60));
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
