package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import nicelee.bilibili.enums.VideoQualityEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.ui.item.ClipInfoPanel;
import nicelee.ui.item.MJButton;
import nicelee.ui.util.AnimeUi;
import nicelee.ui.util.SwingDispatch;
import nicelee.ui.thread.DownloadTaskDispatcher;
import nicelee.ui.thread.DownloadRunnable;

public class TabVideo extends JPanel implements ActionListener, MouseListener {

	private static final long serialVersionUID = -5829023045158490350L;
	private static final Color PANEL_BORDER_COLOR = new Color(205, 210, 216);
	private static final int PREVIEW_CONNECT_TIMEOUT_MS = 10000;
	private static final int PREVIEW_READ_TIMEOUT_MS = 15000;
	// ImageIcon backgroundIcon = new
	// ImageIcon(this.getClass().getResource("/resources/background.jpg"));

	VideoInfo avInfo;// 保存当前Tab 的视频信息

	JLabel lbTabTitle;
	JLabel lbVideoTitle = new JLabel("Av标题");
	JLabel lbAvID = new JLabel("AvID");
	JLabel lbBreif = new JLabel("Av简介");
	ScalableImageLabel lbAvPrivew;
	JPanel jpContent;
	JScrollPane jpScorll;
	JComboBox<String> cbQn; // 清晰度
	JButton btnDownAll; // 批量下载
	JButton btnDownCC; // 批量下载CC字幕
	String currentDisplayPic; // 当前预览图片路径
	JPanel nextPagePanel;  // 下一页面板
	JLabel jlNextPageTips; // 下一页文字提示
	protected JButton btnNextPage; // 下一页
	private JLabel lbLoadStatus;
	private JProgressBar loadProgress;
	private JSplitPane detailSplitPane;
	private final AtomicLong previewRequestSequence = new AtomicLong();
	JButton btnSelectAll;
	JButton btnSelectNone;
	JButton btnInvert;
	JButton btnDownloadSelected;
	JLabel lbSelectionSummary;
	JPanel selectionBar;
	

	public TabVideo(JLabel lbTabTitle) {
		this.lbTabTitle = lbTabTitle;
		init();
	}

	public void init() {
		this.setOpaque(false);
		this.setLayout(new BorderLayout(0, 12));
		this.setBorder(new EmptyBorder(16, 16, 16, 16));

		JPanel header = new JPanel(new BorderLayout(0, 10));
		header.setOpaque(false);
		JPanel metadata = new JPanel(new GridBagLayout());
		metadata.setOpaque(false);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 6, 8);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		lbVideoTitle.setBorder(createInfoBorder());
		lbVideoTitle.addMouseListener(this);
		constraints.gridx = 0;
		constraints.weightx = 1.0;
		metadata.add(lbVideoTitle, constraints);

		lbAvID.setBorder(createInfoBorder());
		lbAvID.addMouseListener(this);
		constraints.gridx = 1;
		constraints.weightx = 0.0;
		constraints.insets = new Insets(0, 0, 6, 0);
		metadata.add(lbAvID, constraints);

		lbBreif.setBorder(createInfoBorder());
		lbBreif.addMouseListener(this);
		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.gridwidth = 2;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(0, 0, 0, 0);
		metadata.add(lbBreif, constraints);
		header.add(metadata, BorderLayout.CENTER);

		JPanel actionRow = new JPanel(new BorderLayout(12, 0));
		actionRow.setOpaque(false);
		JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		loadingPanel.setOpaque(false);
		lbLoadStatus = new JLabel("正在解析作品信息...");
		loadProgress = new JProgressBar();
		loadProgress.setIndeterminate(true);
		loadingPanel.add(loadProgress);
		loadingPanel.add(lbLoadStatus);
		actionRow.add(loadingPanel, BorderLayout.CENTER);

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		controls.setOpaque(false);
		controls.add(new JLabel("优先清晰度"));
		cbQn = new JComboBox<String>();
		for (VideoQualityEnum item : VideoQualityEnum.values()) {
			cbQn.addItem(item.getQuality());
		}
		cbQn.setSelectedItem(Global.tab_qn);
		controls.add(cbQn);

		btnDownAll = new MJButton("批量下载");
		AnimeUi.styleSecondaryButton(btnDownAll);
		btnDownAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Object selectedQuality = cbQn.getSelectedItem();
				if (selectedQuality != null) {
					download(true, VideoQualityEnum.getQN(selectedQuality.toString()));
				}
			}
		});
		controls.add(btnDownAll);

		btnDownCC = new MJButton("字幕下载");
		AnimeUi.styleSecondaryButton(btnDownCC);
		btnDownCC.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				downloadCC();
			}
		});
		controls.add(btnDownCC);

		nextPagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		nextPagePanel.setOpaque(false);
		jlNextPageTips = new JLabel("视频数量不对？");
		btnNextPage = new MJButton("下一页");
		nextPagePanel.add(jlNextPageTips);
		nextPagePanel.add(btnNextPage);
		nextPagePanel.setVisible(false);
		controls.add(nextPagePanel);
		actionRow.add(controls, BorderLayout.EAST);
		header.add(actionRow, BorderLayout.SOUTH);
		this.add(header, BorderLayout.NORTH);

		lbAvPrivew = new ScalableImageLabel("正在解析...", SwingConstants.CENTER);
		lbAvPrivew.setToolTipText("单击复制当前预览图片链接");
		lbAvPrivew.setFont(lbAvPrivew.getFont().deriveFont(Font.BOLD, 32.0f));
		lbAvPrivew.setBorder(BorderFactory.createLineBorder(PANEL_BORDER_COLOR));
		lbAvPrivew.setMinimumSize(new Dimension(300, 260));
		lbAvPrivew.addMouseListener(this);

		jpContent = new JPanel();
		jpContent.setLayout(new javax.swing.BoxLayout(jpContent, javax.swing.BoxLayout.Y_AXIS));
		jpContent.setBorder(new EmptyBorder(8, 8, 8, 8));
		jpContent.setPreferredSize(new Dimension(0, 300));
		jpContent.setOpaque(false);

		jpScorll = new JScrollPane(jpContent);
		jpScorll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		jpScorll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		jpScorll.setBorder(BorderFactory.createLineBorder(PANEL_BORDER_COLOR));
		jpScorll.setMinimumSize(new Dimension(280, 260));
		jpScorll.setOpaque(false);
		jpScorll.getViewport().setOpaque(false);

		detailSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lbAvPrivew, jpScorll);
		detailSplitPane.setBorder(null);
		detailSplitPane.setContinuousLayout(true);
		detailSplitPane.setOneTouchExpandable(true);
		detailSplitPane.setResizeWeight(0.65);
		detailSplitPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				if (detailSplitPane.getWidth() > 0) {
					detailSplitPane.setDividerLocation(0.65);
					detailSplitPane.removeComponentListener(this);
				}
			}
		});
		// 批量选择工具栏
		selectionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		selectionBar.setOpaque(false);
		lbSelectionSummary = new JLabel("已选 0 / 0 集");
		lbSelectionSummary.setForeground(AnimeUi.TEXT_SECONDARY);
		btnSelectAll = new MJButton("全选");
		btnSelectNone = new MJButton("取消全选");
		btnInvert = new MJButton("反选");
		btnDownloadSelected = new MJButton("下载所选");
		AnimeUi.styleSecondaryButton(btnSelectAll);
		AnimeUi.styleSecondaryButton(btnSelectNone);
		AnimeUi.styleSecondaryButton(btnInvert);
		AnimeUi.stylePrimaryButton(btnDownloadSelected);
		btnDownloadSelected.setEnabled(false);
		btnSelectAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (Component comp : jpContent.getComponents()) {
					if (comp instanceof ClipInfoPanel) {
						((ClipInfoPanel) comp).setSelectedForDownload(true);
					}
				}
				updateSelectionSummary();
			}
		});
		btnSelectNone.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (Component comp : jpContent.getComponents()) {
					if (comp instanceof ClipInfoPanel) {
						((ClipInfoPanel) comp).setSelectedForDownload(false);
					}
				}
				updateSelectionSummary();
			}
		});
		btnInvert.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (Component comp : jpContent.getComponents()) {
					if (comp instanceof ClipInfoPanel) {
						ClipInfoPanel clipPanel = (ClipInfoPanel) comp;
						clipPanel.setSelectedForDownload(!clipPanel.isSelectedForDownload());
					}
				}
				updateSelectionSummary();
			}
		});
		btnDownloadSelected.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				downloadSelected();
			}
		});
		selectionBar.add(lbSelectionSummary);
		selectionBar.add(btnSelectAll);
		selectionBar.add(btnSelectNone);
		selectionBar.add(btnInvert);
		selectionBar.add(btnDownloadSelected);
		selectionBar.setVisible(false);

		JPanel centerWrapper = new JPanel(new BorderLayout(0, 8));
		centerWrapper.setOpaque(false);
		centerWrapper.add(selectionBar, BorderLayout.NORTH);
		centerWrapper.add(detailSplitPane, BorderLayout.CENTER);
		this.add(centerWrapper, BorderLayout.CENTER);
		setLoading(true);
	}

	private javax.swing.border.Border createInfoBorder() {
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(PANEL_BORDER_COLOR),
				new EmptyBorder(5, 8, 5, 8));
	}

	public void displayNextPagePanel() {
		nextPagePanel.setVisible(true);
		nextPagePanel.revalidate();
	}

	/**
	 * 切换详情页加载状态。此方法必须在 Swing EDT 调用。
	 */
	public void setLoading(boolean loading) {
		cbQn.setEnabled(!loading);
		btnDownAll.setEnabled(!loading);
		btnDownCC.setEnabled(!loading);
		btnNextPage.setEnabled(!loading);
		loadProgress.setVisible(loading);
		if (selectionBar != null) {
			selectionBar.setVisible(false);
		}
		if (loading) {
			previewRequestSequence.incrementAndGet();
			showPreviewMessage("正在解析...");
			lbLoadStatus.setForeground(new Color(25, 90, 160));
			lbLoadStatus.setText("正在解析作品信息...");
		} else {
			lbLoadStatus.setForeground(new Color(40, 110, 55));
			lbLoadStatus.setText("解析完成");
		}
	}

	public void completeLoading(int clipCount) {
		boolean hasClips = clipCount > 0;
		loadProgress.setVisible(false);
		cbQn.setEnabled(hasClips);
		btnDownAll.setEnabled(hasClips);
		btnDownCC.setEnabled(hasClips);
		btnNextPage.setEnabled(true);
		lbLoadStatus.setForeground(hasClips ? new Color(40, 110, 55) : new Color(145, 95, 20));
		lbLoadStatus.setText(hasClips ? "解析完成，共 " + clipCount + " 个分集" : "解析完成，未找到可下载分集");
		selectionBar.setVisible(hasClips);
		if (hasClips) {
			updateSelectionSummary();
		}
	}

	public void beginRenderingClips(int clipCount) {
		loadProgress.setVisible(clipCount > 0);
		cbQn.setEnabled(false);
		btnDownAll.setEnabled(false);
		btnDownCC.setEnabled(false);
		btnNextPage.setEnabled(false);
		selectionBar.setVisible(false);
		lbLoadStatus.setForeground(new Color(25, 90, 160));
		lbLoadStatus.setText(clipCount > 0 ? "正在生成分集列表：0 / " + clipCount : "正在整理作品信息...");
	}

	public void updateRenderingProgress(int renderedCount, int clipCount) {
		lbLoadStatus.setText("正在生成分集列表：" + renderedCount + " / " + clipCount);
	}

	public void setLoadFailed(String message) {
		previewRequestSequence.incrementAndGet();
		loadProgress.setVisible(false);
		cbQn.setEnabled(false);
		btnDownAll.setEnabled(false);
		btnDownCC.setEnabled(false);
		btnNextPage.setEnabled(false);
		selectionBar.setVisible(false);
		lbLoadStatus.setForeground(new Color(170, 45, 45));
		lbLoadStatus.setText(message);
		showPreviewMessage(message);
	}

	public void loadPreviewImageAsync(final String previewUrl) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					loadPreviewImageAsync(previewUrl);
				}
			});
			return;
		}
		final long requestId = previewRequestSequence.incrementAndGet();
		if (previewUrl == null || previewUrl.trim().isEmpty()) {
			showPreviewMessage("无预览图");
			return;
		}
		showPreviewMessage("正在加载预览图...");
		Thread previewLoader = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final ImageIcon image = readPreviewImage(previewUrl);
					SwingDispatch.runLater(new Runnable() {
						@Override
						public void run() {
							if (previewRequestSequence.get() == requestId) {
								showPreviewImage(image, previewUrl);
							}
						}
					});
				} catch (Exception error) {
					SwingDispatch.runLater(new Runnable() {
						@Override
						public void run() {
							if (previewRequestSequence.get() == requestId) {
								showPreviewMessage("预览图加载失败");
							}
						}
					});
				}
			}
		}, "Thread-PreviewImage");
		previewLoader.setDaemon(true);
		previewLoader.start();
	}

	private ImageIcon readPreviewImage(String previewUrl) throws Exception {
		URL url = new URL(previewUrl);
		String protocol = url.getProtocol();
		if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
			throw new IllegalArgumentException("预览图仅支持 HTTP/HTTPS 地址");
		}
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(PREVIEW_CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(PREVIEW_READ_TIMEOUT_MS);
		if (Global.userAgent != null && !Global.userAgent.trim().isEmpty()) {
			connection.setRequestProperty("User-Agent", Global.userAgent);
		}
		try (InputStream input = connection.getInputStream()) {
			BufferedImage image = ImageIO.read(input);
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw new IllegalStateException("预览图内容无效");
			}
			return new ImageIcon(image);
		}
	}

	private void showPreviewImage(ImageIcon image, String previewUrl) {
		lbAvPrivew.setSourceImage(image.getImage());
		lbAvPrivew.setText("");
		currentDisplayPic = previewUrl;
	}

	public void showPreviewMessage(final String message) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					showPreviewMessage(message);
				}
			});
			return;
		}
		lbAvPrivew.setSourceImage(null);
		lbAvPrivew.setIcon(null);
		lbAvPrivew.setText(message);
		currentDisplayPic = null;
	}
	
	/**
	 * 用于批量下载视频
	 * 
	 * @param downAll
	 * @param qn
	 */
	public void download(boolean downAll, int qn) {
		if (avInfo == null || avInfo.getClips() == null) {
			return;
		}
		int total = avInfo.getClips().values().size();
		if (total == 0) {
			return;
		}
		download(0, qn);
		if (downAll) {
			for (int i = 1; i < total; i++) {
				download(i, qn);
			}
		}
	}

	/**
	 * 用于下载字幕
	 * 
	 */
	public void downloadCC() {
		if (avInfo == null || avInfo.getClips() == null) {
			return;
		}
		int total = avInfo.getClips().values().size();
		for (int i = 0; i < total; i++) {
			download(i, 800);
		}
	}

	/**
	 * 下载第i个视频
	 * 
	 * @param i
	 * @param qn
	 */
	private void download(int i, int qn) {
		try {
			ClipInfo clip = (ClipInfo) avInfo.getClips().values().toArray()[i];
			DownloadRunnable downThread = new DownloadRunnable(avInfo, clip, qn);
			DownloadTaskDispatcher.submit(downThread);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void downloadClip(ClipInfo clip) {
		try {
			Object selectedQuality = cbQn.getSelectedItem();
			int qn = selectedQuality != null ? VideoQualityEnum.getQN(selectedQuality.toString()) : 0;
			DownloadRunnable downThread = new DownloadRunnable(avInfo, clip, qn);
			DownloadTaskDispatcher.submit(downThread);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void downloadSelected() {
		if (avInfo == null || avInfo.getClips() == null) {
			return;
		}
		for (Component comp : jpContent.getComponents()) {
			if (comp instanceof ClipInfoPanel) {
				ClipInfoPanel clipPanel = (ClipInfoPanel) comp;
				if (clipPanel.isSelectedForDownload()) {
					downloadClip(clipPanel.getClip());
				}
			}
		}
	}

	public void updateSelectionSummary() {
		int selected = 0;
		int total = 0;
		for (Component comp : jpContent.getComponents()) {
			if (comp instanceof ClipInfoPanel) {
				total++;
				if (((ClipInfoPanel) comp).isSelectedForDownload()) {
					selected++;
				}
			}
		}
		lbSelectionSummary.setText("已选 " + selected + " / " + total + " 集");
		btnDownloadSelected.setEnabled(selected > 0);
	}

//	@Override
//	public void paintComponent(Graphics g) {
////		// super.paintComponent(g);
//		g.drawImage(backgroundIcon.getImage(), 0, 0, this.getSize().width, this.getSize().height, this.getParent());
//		this.setOpaque(false);
//	}
	@Override
	public void actionPerformed(ActionEvent e) {

	}

	public JLabel getLbTabTitle() {
		return lbTabTitle;
	}

	public void setLbTabTitle(JLabel lbTabTitle) {
		this.lbTabTitle = lbTabTitle;
	}

	public JLabel getLbVideoTitle() {
		return lbVideoTitle;
	}

	public void setLbVideoTitle(JLabel lbVideoTitle) {
		this.lbVideoTitle = lbVideoTitle;
	}

	public JLabel getLbAvID() {
		return lbAvID;
	}

	public void setLbAvID(JLabel lbAvID) {
		this.lbAvID = lbAvID;
	}

	public JLabel getLbBreif() {
		return lbBreif;
	}

	public void setLbBreif(JLabel lbBreif) {
		this.lbBreif = lbBreif;
	}

	public JLabel getLbAvPrivew() {
		return lbAvPrivew;
	}

	public JScrollPane getJpScorll() {
		return jpScorll;
	}

	public void setJpScorll(JScrollPane jpScorll) {
		this.jpScorll = jpScorll;
	}

	public JPanel getJpContent() {
		return jpContent;
	}

	public void setJpContent(JPanel jpContent) {
		this.jpContent = jpContent;
	}

	public JSplitPane getDetailSplitPane() {
		return detailSplitPane;
	}

	public JLabel getLoadStatusLabel() {
		return lbLoadStatus;
	}

	public JProgressBar getLoadProgress() {
		return loadProgress;
	}

	public boolean areDownloadActionsEnabled() {
		return cbQn.isEnabled() && btnDownAll.isEnabled() && btnDownCC.isEnabled();
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		JLabel label = (JLabel) e.getSource();
		// 获取系统剪贴板
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		String txtToCopy = null;
		if (label == lbAvPrivew) {
			txtToCopy = currentDisplayPic != null ? currentDisplayPic
					: avInfo == null ? null : avInfo.getVideoPreview();
		} else {
			txtToCopy = label.getText();
		}
		if (txtToCopy != null) {
			// 封装文本内容
			Transferable trans = new StringSelection(txtToCopy);
			// 把文本内容设置到系统剪贴板
			clipboard.setContents(trans, null);
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		JLabel label = (JLabel) e.getSource();
		label.setBorder(BorderFactory.createLineBorder(Color.black, 3));
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		JLabel label = (JLabel) e.getSource();
		label.setBorder(label == lbAvPrivew ? BorderFactory.createLineBorder(PANEL_BORDER_COLOR) : createInfoBorder());

	}

	@Override
	public void mouseEntered(MouseEvent e) {

	}

	@Override
	public void mouseExited(MouseEvent e) {

	}

	public VideoInfo getAvInfo() {
		return avInfo;
	}

	public void setAvInfo(VideoInfo avInfo) {
		this.avInfo = avInfo;
	}

	public String getCurrentDisplayPic() {
		return currentDisplayPic;
	}

	public void setCurrentDisplayPic(String currentDisplayPic) {
		this.currentDisplayPic = currentDisplayPic;
	}

	private static final class ScalableImageLabel extends JLabel {
		private static final long serialVersionUID = 1L;
		private Image sourceImage;

		private ScalableImageLabel(String text, int horizontalAlignment) {
			super(text, horizontalAlignment);
		}

		private void setSourceImage(Image sourceImage) {
			this.sourceImage = sourceImage;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (sourceImage == null) {
				return;
			}
			int imageWidth = sourceImage.getWidth(this);
			int imageHeight = sourceImage.getHeight(this);
			if (imageWidth <= 0 || imageHeight <= 0) {
				return;
			}
			Insets insets = getInsets();
			int availableWidth = Math.max(1, getWidth() - insets.left - insets.right);
			int availableHeight = Math.max(1, getHeight() - insets.top - insets.bottom);
			double scale = Math.min((double) availableWidth / imageWidth, (double) availableHeight / imageHeight);
			int targetWidth = Math.max(1, (int) Math.round(imageWidth * scale));
			int targetHeight = Math.max(1, (int) Math.round(imageHeight * scale));
			int x = insets.left + (availableWidth - targetWidth) / 2;
			int y = insets.top + (availableHeight - targetHeight) / 2;
			Graphics2D imageGraphics = (Graphics2D) graphics.create();
			try {
				imageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				imageGraphics.drawImage(sourceImage, x, y, targetWidth, targetHeight, this);
			} finally {
				imageGraphics.dispose();
			}
		}
	}
}
