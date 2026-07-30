package nicelee.bilibili.downloaders.impl;

import java.io.File;
import java.util.HashMap;

import nicelee.ui.item.JOptionPane;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.enums.StatusEnum;
import nicelee.bilibili.util.FFmpegBinaryManifest;
import nicelee.bilibili.util.Logger;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.SysUtil;
import nicelee.bilibili.util.VerifiedBinaryInstaller;

@Bilibili(name = "ffmpeg-downloader", weight = 22, type = "downloader", note = "ffmpeg下载")
public class FFmpegDownloader extends FLVDownloader {

	@Override
	public boolean matches(String url) {
		if (url.contains("ffmpeg")) {
			return true;
		}
		return false;
	}

	/**
	 * 下载matches
	 * 
	 * @param url
	 * @param avId
	 * @param qn 
	 * @param page 
	 * @return
	 */
	@Override
	public boolean download(String url, String avId, int qn, int page) {
		String ffmpegEXE = "ffmpeg" + SysUtil.getEXE_SUFFIX();
		String os = SysUtil.getOS();
		String arch = SysUtil.getARCH();
		String expectedSha256 = FFmpegBinaryManifest.expectedSha256(os, arch);
		if (expectedSha256 == null)
			throw new SecurityException("当前平台没有可信的 ffmpeg SHA-256 清单，已拒绝自动下载");
		String stagedName = ffmpegEXE + ".download";
		File destination = ResourcesUtil.sourceOf(ffmpegEXE);
		convertingStatus = StatusEnum.NONE;
		currentTask = 1;
		if (file == null) {
			file = ResourcesUtil.sourceOf(stagedName);
		}
		util.setSavePath(ResourcesUtil.baseDirectory());
		boolean succ = util.download(url, stagedName, new HashMap<>());
		if (succ) {
			try {
				VerifiedBinaryInstaller.installSha256(util.getFileDownload(), destination, expectedSha256);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "ffmpeg 下载文件未通过安全校验，已拒绝安装。", "安全校验失败",
						JOptionPane.WARNING_MESSAGE);
				throw new RuntimeException("ffmpeg SHA-256 校验或安装失败", e);
			}
			file = destination;
			Logger.println("ffmpeg SHA-256 校验通过");
			sumSuccessDownloaded += util.getTotalFileSize();
			util.reset();
			JOptionPane.showMessageDialog(null, "ffmpeg 已安全安装，转码功能在重启后可使用。", "成功!!",
					JOptionPane.INFORMATION_MESSAGE);
		}
		return succ;
	}

}
