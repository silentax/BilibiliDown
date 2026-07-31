package nicelee.ui.thread;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
	private static final int CLIP_RENDER_BATCH_SIZE = 12;

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
			final DetailResult result = loadDetail();
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					try {
						applyDetail(result);
					} catch (RuntimeException error) {
						handleFailure(error);
					}
				}
			});
		} catch (Exception error) {
			final Exception loadError = error;
			SwingDispatch.runLater(new Runnable() {
				@Override
				public void run() {
					handleFailure(loadError);
				}
			});
		}
	}

	private void handleFailure(Exception error) {
		error.printStackTrace();
		video.getLbTabTitle().setText("解析失败");
		video.setLoadFailed("解析失败，请检查输入或网络");
		finish(false, "解析失败");
		JOptionPaneManager.alertErrMsgWithNewThread("作品解析失败", ResourcesUtil.detailsOfException(error));
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
		String previewUrl = avInfo.getVideoPreview();
		if (!clipList.isEmpty()) {
			String clipPreview = clipList.get(0).getPicPreview();
			if (clipPreview != null && !clipPreview.trim().isEmpty()) {
				previewUrl = clipPreview;
			}
		}
		return new DetailResult(avInfo, clipList, pageable, previewUrl);
	}

	private void applyDetail(DetailResult result) {
		VideoInfo avInfo = result.videoInfo;
		if (result.pageable) {
			Logger.println("当前为分页查询");
			video.displayNextPagePanel();
		}
		video.setAvInfo(avInfo);
		video.getLbAvID().setText(avInfo.getVideoId());
		if (Global.autoDisplayPreviewPic) {
			video.loadPreviewImageAsync(result.previewUrl);
		} else {
			video.showPreviewMessage("已关闭预览图显示");
		}
		String videoName = avInfo.getVideoName();
		if (videoName == null || videoName.trim().isEmpty()) {
			videoName = avInfo.getVideoId() == null ? "未命名作品" : avInfo.getVideoId();
		}
		video.getLbBreif().setText(avInfo.getBrief());
		video.getLbBreif().setToolTipText(avInfo.getBrief());
		video.getLbVideoTitle().setText(videoName);
		video.getLbVideoTitle().setToolTipText(videoName);
		String title = videoName;
		if (title.length() >= 12) {
			title = title.substring(0, 9) + "...";
		}
		video.getLbTabTitle().setText(title);

		JPanel content = video.getJpContent();
		content.removeAll();
		content.setPreferredSize(new Dimension(0, 300));
		content.revalidate();
		content.repaint();
		video.beginRenderingClips(result.clips.size());
		renderClipBatch(result, 0, videoName);
	}

	private void renderClipBatch(final DetailResult result, int startIndex, final String videoName) {
		try {
			JPanel content = video.getJpContent();
			int endIndex = Math.min(result.clips.size(), startIndex + CLIP_RENDER_BATCH_SIZE);
			for (int index = startIndex; index < endIndex; index++) {
				content.add(new ClipInfoPanel(result.videoInfo, result.clips.get(index), video));
			}
			content.setPreferredSize(new Dimension(0, Math.max(300, 178 * endIndex)));
			content.revalidate();
			content.repaint();
			if (endIndex < result.clips.size()) {
				video.updateRenderingProgress(endIndex, result.clips.size());
				final int nextIndex = endIndex;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						renderClipBatch(result, nextIndex, videoName);
					}
				});
				return;
			}
			video.completeLoading(result.clips.size());
			finish(true, "解析完成：" + videoName);
		} catch (RuntimeException error) {
			handleFailure(error);
		}
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

		private DetailResult(VideoInfo videoInfo, List<ClipInfo> clips, boolean pageable, String previewUrl) {
			this.videoInfo = videoInfo;
			this.clips = clips;
			this.pageable = pageable;
			this.previewUrl = previewUrl;
		}
	}
}
