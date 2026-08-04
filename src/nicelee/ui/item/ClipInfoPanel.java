package nicelee.ui.item;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.custom.System;
import nicelee.ui.Global;
import nicelee.ui.TabVideo;
import nicelee.ui.thread.DownloadTaskDispatcher;
import nicelee.ui.thread.DownloadRunnable;
import nicelee.ui.util.AnimeUi;

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
	private JCheckBox selectedBox;

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
		this.setLayout(new BorderLayout(12, 0));
		this.setBorder(AnimeUi.cardBorder(9, 12));
		this.setPreferredSize(new Dimension(560, 76));
		this.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
		this.setBackground(AnimeUi.SURFACE);
		this.setOpaque(true);

		JPanel selector = new JPanel(new BorderLayout(8, 0));
		selector.setOpaque(false);
		selectedBox = new JCheckBox();
		selectedBox.setOpaque(false);
		selectedBox.setToolTipText("选择此分集用于批量下载");
		selectedBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				setBackground(selectedBox.isSelected() ? AnimeUi.ACCENT_SOFT : AnimeUi.SURFACE);
				if (tabVideo != null) {
					tabVideo.updateSelectionSummary();
				}
			}
		});
		JLabel pageBadge = new JLabel("P" + clip.getPage(), JLabel.CENTER);
		pageBadge.setOpaque(true);
		pageBadge.setForeground(AnimeUi.ACCENT);
		pageBadge.setBackground(AnimeUi.ACCENT_SOFT);
		pageBadge.setBorder(new EmptyBorder(5, 8, 5, 8));
		pageBadge.setFont(pageBadge.getFont().deriveFont(Font.BOLD));
		selector.add(selectedBox, BorderLayout.WEST);
		selector.add(pageBadge, BorderLayout.CENTER);
		this.add(selector, BorderLayout.WEST);

		// 分情况显示
		boolean isPic = ResourcesUtil.isPicture(clip);
		if(clip.getListName() != null || isPic) {
			labelTitle = new JLabel(clip.getAvTitle() + " · " + clip.getTitle());
		}else {
			labelTitle = new JLabel(clip.getTitle());
		}
		labelTitle.setFont(labelTitle.getFont().deriveFont(Font.BOLD, 14.0f));
		labelTitle.setForeground(AnimeUi.TEXT_PRIMARY);
		labelTitle.addMouseListener(this);
		labelTitle.setToolTipText(clip.getAvTitle() + " · " + clip.getTitle());
		JLabel metadata = new JLabel("分集 " + clip.getRemark() + "   ·   " + clip.getAvId());
		metadata.setForeground(AnimeUi.TEXT_SECONDARY);
		JPanel info = new JPanel(new GridLayout(2, 1, 0, 4));
		info.setOpaque(false);
		info.add(labelTitle);
		info.add(metadata);
		this.add(info, BorderLayout.CENTER);

		buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
		buttonPanel.setOpaque(false);
		this.add(buttonPanel, BorderLayout.EAST);

		if(!isPic) {
			JButton btnDanmuku = new MJButton("弹幕");
			AnimeUi.styleSecondaryButton(btnDanmuku);
			btnDanmuku.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					DownloadRunnable downThread = new DownloadRunnable(video, clip, 801);
					DownloadTaskDispatcher.submit(downThread);
				}
			});
			buttonPanel.add(btnDanmuku);
		}
		if(!isPic) {
			JButton btn = new MJButton("字幕");
			AnimeUi.styleSecondaryButton(btn);
			initQnBtn(800, btn);
		}
		JButton btnDownload = new MJButton("下载");
		AnimeUi.stylePrimaryButton(btnDownload);
		btnDownload.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if (tabVideo != null) {
					tabVideo.downloadClip(clip);
				}
			}
		});
		buttonPanel.add(btnDownload);
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

	public boolean isSelectedForDownload() {
		return selectedBox.isSelected();
	}

	public void setSelectedForDownload(boolean selected) {
		selectedBox.setSelected(selected);
		setBackground(selected ? AnimeUi.ACCENT_SOFT : AnimeUi.SURFACE);
	}

	public ClipInfo getClip() {
		return clip;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getClickCount() == 1 && tabVideo != null) {
			String preview = clip.getPicPreview();
			if (preview != null) {
				tabVideo.loadPreviewImageAsync(preview);
			}
			return;
		}
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
		labelTitle.setForeground(AnimeUi.ACCENT);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		labelTitle.setForeground(AnimeUi.TEXT_PRIMARY);
	}

	@Override
	public void mouseEntered(MouseEvent e) {

	}

	@Override
	public void mouseExited(MouseEvent e) {

	}
}
