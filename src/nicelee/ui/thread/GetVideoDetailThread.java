package nicelee.ui.thread;

import java.awt.Dimension;
import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.parsers.impl.AbstractPageQueryParser;
import nicelee.bilibili.util.Logger;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.ui.Global;
import nicelee.ui.TabVideo;
import nicelee.ui.item.ClipInfoPanel;
import nicelee.ui.item.JOptionPaneManager;
import nicelee.ui.util.SwingDispatch;

/**
 * 在后台解析作品信息，并仅在 Swing EDT 上提交界面变更。
 */
public class GetVideoDetailThread extends Thread {

	public interface Listener {
		void onFinished(boolean success, String message);
	}

	private final TabVideo video;
	private final String searchContent;
	private final Listener listener;

	public GetVideoDetailThread(TabVideo video, String searchContent) {
		this(video, searchContent, null);
	}

	public GetVideoDetailThread(TabVideo video, String searchContent, Listener listener) {
		this.video = video;
		this.searchContent = searchContent;
		this.listener = listener;
		setName("Thread-GetVideoInfo");
		setDaemon(true);
	}

	@Override
	public void run() {
		try {
			DetailResult result = loadDetail();
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					applyDetail(result);
					finish(true, "解析完成：" + result.videoInfo.getVideoName());
				}
			});
		} catch (Exception error) {
			error.printStackTrace();
			final String details = ResourcesUtil.detailsOfException(error);
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					video.getLbTabTitle().setText("解析失败");
					video.setLoadFailed("解析失败，请检查输入或网络");
					finish(false, "解析失败");
				}
			});
			JOptionPaneManager.alertErrMsgWithNewThread("作品解析失败", details);
		}
	}

	private DetailResult loadDetail() throws Exception {
		INeedAV avs = new INeedAV();
		String validId = avs.getValidID(searchContent);
		if (validId == null || validId.trim().isEmpty()) {
			throw new IllegalArgumentException("无法从输入内容中识别 B 站作品 ID");
		}
		Logger.println("当前解析的id为：" + validId);
		VideoInfo avInfo = avs.getVideoDetail(validId, Global.downloadFormat, false);
		boolean pageable = avs.getInputParser(validId).getParser() instanceof AbstractPageQueryParser;

		Collection<ClipInfo> clips = avInfo.getClips().values();
		List<ClipInfo> clipList = new ArrayList<ClipInfo>(clips);
		String previewUrl = null;
		ImageIcon previewIcon = null;
		boolean previewInvalid = false;
		if (Global.autoDisplayPreviewPic) {
			previewUrl = clipList.isEmpty() ? avInfo.getVideoPreview() : clipList.get(0).getPicPreview();
			if (previewUrl != null && !previewUrl.trim().isEmpty()) {
				try {
					ImageIcon source = new ImageIcon(new URL(previewUrl));
					Image scaled = source.getImage().getScaledInstance(700, 460, Image.SCALE_SMOOTH);
					previewIcon = new ImageIcon(scaled);
				} catch (Exception e) {
					previewInvalid = true;
				}
			} else {
				previewInvalid = true;
			}
		}
		return new DetailResult(avInfo, clipList, pageable, previewUrl, previewIcon, previewInvalid);
	}

	private void applyDetail(DetailResult result) {
		VideoInfo avInfo = result.videoInfo;
		if (result.pageable) {
			Logger.println("当前为分页查询");
			video.displayNextPagePanel();
		}
		video.setAvInfo(avInfo);
		video.getLbAvID().setText(avInfo.getVideoId());
		video.setCurrentDisplayPic(result.previewUrl);
		if (Global.autoDisplayPreviewPic) {
			if (result.previewIcon != null) {
				video.getLbAvPrivew().setIcon(result.previewIcon);
				video.getLbAvPrivew().setText("");
			} else {
				video.getLbAvPrivew().setIcon(null);
				video.getLbAvPrivew().setText(result.previewInvalid ? "无效预览图" : "无预览图");
			}
		} else {
			video.getLbAvPrivew().setIcon(null);
			video.getLbAvPrivew().setText("不显示预览");
		}
		video.getLbBreif().setText(avInfo.getBrief());
		video.getLbBreif().setToolTipText(avInfo.getBrief());
		video.getLbVideoTitle().setText(avInfo.getVideoName());
		video.getLbVideoTitle().setToolTipText(avInfo.getVideoName());
		String title = avInfo.getVideoName();
		if (title.length() >= 12) {
			title = title.substring(0, 9) + "...";
		}
		video.getLbTabTitle().setText(title);

		JPanel content = video.getJpContent();
		content.removeAll();
		content.setPreferredSize(new Dimension(340, 175 * result.clips.size()));
		for (ClipInfo clip : result.clips) {
			content.add(new ClipInfoPanel(avInfo, clip));
		}
		content.revalidate();
		content.repaint();
		video.setLoading(false);
	}

	private void finish(boolean success, String message) {
		if (listener != null) {
			listener.onFinished(success, message);
		}
	}

	private static final class DetailResult {
		private final VideoInfo videoInfo;
		private final List<ClipInfo> clips;
		private final boolean pageable;
		private final String previewUrl;
		private final ImageIcon previewIcon;
		private final boolean previewInvalid;

		private DetailResult(VideoInfo videoInfo, List<ClipInfo> clips, boolean pageable, String previewUrl,
				ImageIcon previewIcon, boolean previewInvalid) {
			this.videoInfo = videoInfo;
			this.clips = clips;
			this.pageable = pageable;
			this.previewUrl = previewUrl;
			this.previewIcon = previewIcon;
			this.previewInvalid = previewInvalid;
		}
	}
}
