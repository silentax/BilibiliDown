package nicelee.ui.item;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import nicelee.bilibili.enums.AudioQualityEnum;
import nicelee.bilibili.enums.VideoQualityEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.custom.System;
import nicelee.ui.Global;
import nicelee.ui.TabVideo;
import nicelee.ui.thread.DownloadTaskDispatcher;
import nicelee.ui.thread.DownloadRunnable;

public class ClipInfoPanel extends JPanel implements MouseListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -752743062676819403L;
	String avTitle;
	VideoInfo video;
	ClipInfo clip;
	private final TabVideo tabVideo;

	private JLabel labelTitle;
	private JPanel buttonPanel;
	private long lastMousePressed;

	public ClipInfoPanel(VideoInfo video, ClipInfo clip) {
		this(video, clip, null);
	}

	public ClipInfoPanel(VideoInfo video, ClipInfo clip, TabVideo tabVideo) {
		this.video = video;
		this.clip = clip;
		this.tabVideo = tabVideo;
		this.avTitle = clip.getAvTitle();
		initUI();
	}

	void initUI() {
		this.setLayout(new BorderLayout(0, 8));
		this.setBorder(BorderFactory.createLineBorder(new Color(205, 210, 216)));
		this.setPreferredSize(new Dimension(280, 170));
		// 分情况显示
		boolean isPic = ResourcesUtil.isPicture(clip);
		if(clip.getListName() != null || isPic) {
			labelTitle = new JLabel(clip.getRemark() + " - " + clip.getAvTitle()+ " " +clip.getTitle(), JLabel.CENTER);
		}else {
			labelTitle = new JLabel(clip.getRemark() + " - " + clip.getTitle(), JLabel.CENTER);
		}
		labelTitle.addMouseListener(this);
		labelTitle.setToolTipText(clip.getAvTitle() + clip.getTitle());
		labelTitle.setBorder(new EmptyBorder(8, 8, 0, 8));
		this.setOpaque(false);
		this.add(labelTitle, BorderLayout.NORTH);

		buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		buttonPanel.setOpaque(false);
		this.add(buttonPanel, BorderLayout.CENTER);

		if(!isPic) {
			JButton btnDanmuku = new MJButton("弹幕");
			btnDanmuku.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					DownloadRunnable downThread = new DownloadRunnable(video, clip, 801);
					DownloadTaskDispatcher.submit(downThread);
				}
			});
			buttonPanel.add(btnDanmuku);
		}
		
		for (final int qn : clip.getLinks().keySet()) {
			if(qn >= 800)
				continue;
			// JButton btn = new JButton("清晰度: " + qn);
			String qnName = VideoQualityEnum.getQualityDescript(qn);
			if (qnName == null)
				qnName = AudioQualityEnum.getQualityDescript(qn);
			JButton btn = null;
			if (qnName != null && !isPic) {
				btn = new MJButton(qnName);
			} else {
				btn = new MJButton("清晰度: " + qn);
			}
			initQnBtn(qn, btn);
		}
		if(!isPic) {
			JButton btn = new MJButton("字幕");
			initQnBtn(800, btn);
		}
	}

	/**
	 * @param qn
	 * @param btn
	 */
	private void initQnBtn(final int qn, JButton btn) {
		btn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				DownloadRunnable downThread = new DownloadRunnable(video, clip, qn);
				DownloadTaskDispatcher.submit(downThread);
			}
		});
		buttonPanel.add(btn);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getClickCount() < 2) {
			return;
		}
		String txtToCopy = clip.getAvTitle() + clip.getTitle() + " " +clip.getAvId();
		// 获取系统剪贴板
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		// 封装文本内容
		Transferable trans = new StringSelection(txtToCopy);
		// 把文本内容设置到系统剪贴板
		clipboard.setContents(trans, null);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		lastMousePressed = System.currentTimeMillis();
		labelTitle.setBorder(BorderFactory.createLineBorder(Color.red));
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		labelTitle.setBorder(new EmptyBorder(8, 8, 0, 8));
		long timeTouched = System.currentTimeMillis() - lastMousePressed;
		if(timeTouched >= 500 && tabVideo != null) {
			String toDisplay = clip.getPicPreview();
			if(toDisplay != null && !toDisplay.equals(tabVideo.getCurrentDisplayPic())) {
				tabVideo.loadPreviewImageAsync(toDisplay);
			}
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {

	}

	@Override
	public void mouseExited(MouseEvent e) {

	}
}
