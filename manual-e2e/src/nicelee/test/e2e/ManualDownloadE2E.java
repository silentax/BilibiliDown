package nicelee.test.e2e;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.enums.DownloadModeEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.CmdUtil;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.ui.Global;

/**
 * Opt-in real-network acceptance test. It is intentionally kept out of the
 * regular check lifecycle and only downloads the first part of a video.
 */
public final class ManualDownloadE2E {

	private static final Pattern VIDEO_PATH = Pattern.compile("^/video/(BV[0-9A-Za-z]+)/$");

	private ManualDownloadE2E() {
	}

	public static void main(String[] args) throws Exception {
		String canonicalUrl = requireCanonicalVideoUrl(System.getProperty("e2e.url"));
		DownloadModeEnum mode = parseMode(System.getProperty("e2e.mode", "all"));
		File dataDirectory = requireDataDirectory(System.getProperty("e2e.dataDir"));
		File outputDirectory = ResourcesUtil.resolveUnderDirectory(dataDirectory, "download");
		if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
			throw new IllegalStateException("Unable to create isolated E2E output directory");
		}

		ConfigUtil.initConfigs();
		configureIsolatedRun(outputDirectory, mode);
		HttpCookies.setGlobalCookies(null);

		INeedAV application = new INeedAV();
		String videoId = application.getValidID(canonicalUrl);
		if (videoId == null || videoId.length() == 0) {
			throw new IllegalStateException("The canonical URL did not resolve to a video ID");
		}

		VideoInfo video = application.getVideoDetail(videoId, Global.downloadFormat, false);
		if (video == null || video.getClips() == null || video.getClips().isEmpty()) {
			throw new IllegalStateException("No downloadable parts were returned");
		}
		Iterator<ClipInfo> clips = video.getClips().values().iterator();
		ClipInfo firstClip = clips.next();
		String downloadUrl = application.getInputParser(videoId).getVideoLink(firstClip.getAvId(),
				String.valueOf(firstClip.getcId()), 120, Global.downloadFormat);
		if (downloadUrl == null || downloadUrl.length() == 0) {
			throw new IllegalStateException("No download URL was returned for the first part");
		}
		if (mode == DownloadModeEnum.AudioOnly && !downloadUrl.startsWith("#")) {
			throw new IllegalStateException("Audio-only parsing did not return an audio-only download plan");
		}

		int realQuality = application.getInputParser(videoId).getVideoLinkQN();
		boolean downloaded = application.downloadClip(downloadUrl, firstClip.getAvId(), realQuality,
				firstClip.getPage());
		File result = application.getDownloader().file();
		if (!downloaded || result == null || !result.isFile() || result.length() <= 0) {
			throw new IllegalStateException("The first-part download did not produce a non-empty final file");
		}
		File canonicalOutput = outputDirectory.getCanonicalFile();
		File canonicalResult = result.getCanonicalFile();
		if (!canonicalResult.getParentFile().equals(canonicalOutput)) {
			throw new IllegalStateException("The final file escaped the isolated E2E output directory");
		}

		System.out.printf("MANUAL_E2E_OK mode=%s parts=%d bytes=%d file=%s%n", mode.name(),
				video.getClips().size(), result.length(), result.getName());
	}

	private static void configureIsolatedRun(File outputDirectory, DownloadModeEnum mode) throws Exception {
		Global.downloadMode = mode;
		Global.downloadFormat = Global.MP4;
		Global.savePath = outputDirectory.getCanonicalPath() + File.separator;
		Global.thumbUpAfterDownloaded = false;
		Global.playSoundAfterMissionComplete = false;
		Global.useRepo = false;
		Global.saveToRepo = false;
		Global.doRenameAfterComplete = false;
		Global.multiThreadCnt = 0;
		Global.checkDashUrl = false;
		Global.debugCmd = false;
		CmdUtil.FFMPEG_PATH = System.getProperty("e2e.ffmpeg", "ffmpeg");
	}

	private static String requireCanonicalVideoUrl(String value) throws Exception {
		if (value == null || value.trim().length() == 0) {
			throw new IllegalArgumentException("A canonical Bilibili video URL is required");
		}
		URI uri = new URI(value.trim());
		if (!"https".equals(uri.getScheme()) || !"www.bilibili.com".equals(uri.getHost()) || uri.getPort() != -1
				|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new IllegalArgumentException("Use a canonical HTTPS Bilibili video URL without query or fragment");
		}
		Matcher matcher = VIDEO_PATH.matcher(uri.getPath());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Use the canonical form https://www.bilibili.com/video/BV.../");
		}
		return "https://www.bilibili.com/video/" + matcher.group(1) + "/";
	}

	private static DownloadModeEnum parseMode(String value) {
		if ("audio".equalsIgnoreCase(value)) {
			return DownloadModeEnum.AudioOnly;
		}
		if ("video".equalsIgnoreCase(value)) {
			return DownloadModeEnum.VideoOnly;
		}
		if ("all".equalsIgnoreCase(value)) {
			return DownloadModeEnum.All;
		}
		throw new IllegalArgumentException("e2eMode must be one of: all, video, audio");
	}

	private static File requireDataDirectory(String value) throws Exception {
		if (value == null || value.trim().length() == 0) {
			throw new IllegalArgumentException("An isolated E2E data directory is required");
		}
		File directory = new File(value).getCanonicalFile();
		if (!directory.isDirectory() || !directory.canWrite()) {
			throw new IllegalArgumentException("The isolated E2E data directory is not writable");
		}
		return directory;
	}
}
