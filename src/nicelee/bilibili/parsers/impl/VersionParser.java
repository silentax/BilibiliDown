package nicelee.bilibili.parsers.impl;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.LegacyUpdatePolicy;

/**
 * 旧版更新包解析器的兼容占位类。
 */
@Bilibili(name = "Version", note = "旧版自动更新已禁用")
public class VersionParser extends AbstractBaseParser {

	public VersionParser(Object... obj) {
		super(obj);
	}

	@Override
	public boolean matches(String input) {
		return false;
	}

	@Override
	public String validStr(String input) {
		return input;
	}

	@Override
	public String getVideoLink(String avId, String cid, int qn, int downFormat) {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
		return null;
	}

	@Override
	public VideoInfo result(String input, int videoFormat, boolean getVideoLink) {
		LegacyUpdatePolicy.requireAutomaticUpdateEnabled();
		return null;
	}
}
