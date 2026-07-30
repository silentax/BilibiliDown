package nicelee.bilibili.downloaders.impl;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.util.LegacyUpdatePolicy;

/**
 * 旧版正式版更新下载器的兼容占位类。
 *
 * <p>保留类名以避免外部配置加载失败，但不再匹配、下载或执行更新包。</p>
 */
@Bilibili(name = "version-downloader", type = "downloader", note = "旧版自动更新已禁用")
public class VersionDownloader extends FLVDownloader {

	@Override
	public boolean matches(String url) {
		return false;
	}

	@Override
	public boolean download(String url, String avId, int qn, int page) {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
		return false;
	}
}
