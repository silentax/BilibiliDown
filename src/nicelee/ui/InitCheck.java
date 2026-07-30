package nicelee.ui;

import java.io.File;
import java.nio.file.Files;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.CmdUtil;
import nicelee.bilibili.util.FFmpegLocator;
import nicelee.bilibili.util.Logger;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.SysUtil;
import nicelee.ui.item.JOptionPane;
import nicelee.ui.thread.DownloadRunnable;

/**
 * 初始化检查要放在配置文件读取之后
 *
 */
public class InitCheck {

	public static void main(String[] args) {
		checkFileAccess();
		Global.ffmpegPath = "ffmpeg";
		checkFFmpeg(true);
	}

	public static void checkFileAccess() {
		File f = ResourcesUtil.baseDirFile();
		if (!Files.isWritable(f.toPath())) {
			String tips = "检测到程序对于数据目录没有“写”权限，可能无法正常工作。\n"
					+ "建议设置JVM参数 -Dbilibili.prop.dataDirPath={dataDirPath} 指定有读写权限的数据目录位置。\n当前数据目录为: "
					+ f.getAbsolutePath();
			JOptionPane.showMessageDialog(null, tips);
		}
	}

	public static void checkFFmpeg(boolean isFFmpegSupported) {
		CmdUtil.DEFAULT_WORKING_DIR = ResourcesUtil.baseDirFile();
		String ffmpeg = FFmpegLocator.locate(Global.ffmpegPath, ResourcesUtil.baseDirFile());
		if (ffmpeg != null) {
			CmdUtil.FFMPEG_PATH = ffmpeg;
			Logger.println("ffmpeg可用: " + ffmpeg);
			return;
		}

		if (isFFmpegSupported) {
			Object[] options = { "是", "否" };
			int m = JOptionPane.showOptionDialog(null,
					"检测到当前没有ffmpeg环境, mp4及小部分flv文件将无法转码或合并.\r\n     是否下载ffmpeg(自编译, 3M左右)?", "请选择：",
					JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
			Logger.println(m);
			if (m == 0) {
				VideoInfo avInfo = new INeedAV().getVideoDetail("ffmpeg", 0, false);
				DownloadRunnable downThread = new DownloadRunnable(avInfo, avInfo.getClips().get(1234L), 0);
				Global.queryThreadPool.execute(downThread);
				return;
			}
		}

		JOptionPane.showMessageDialog(null, missingFFmpegMessage(), "请注意!!",
				JOptionPane.WARNING_MESSAGE);
	}

	private static String missingFFmpegMessage() {
		String installHint;
		if (SysUtil.isMac())
			installHint = "macOS 可执行: brew install ffmpeg";
		else if (SysUtil.isWindows())
			installHint = "Windows 可执行: winget install Gyan.FFmpeg";
		else
			installHint = "请使用系统包管理器安装 ffmpeg";
		return "当前没有可用的 ffmpeg，音视频合并或转码将不可用。\n" + installHint
				+ "\n安装后重启应用，或在配置页设置 bilibili.ffmpegPath。";
	}

}
