package me.aap.fermata.addon.tv.m3u;

/**
 * Parsing of TV source item ids.
 */
public final class TvM3uIds {

	private TvM3uIds() {
	}

	/**
	 * Whether the id is a group, track, kind folder or EPG item of the given source:
	 * <ul>
	 * <li>tvm3ug:&lt;gid&gt;:&lt;srcId&gt;:&lt;name&gt;</li>
	 * <li>tvm3ut:&lt;gid&gt;:&lt;tid&gt;:&lt;srcId&gt;:&lt;rid&gt;</li>
	 * <li>tvm3uk:&lt;kind&gt;:&lt;srcId&gt;</li>
	 * <li>tvm3ue:&lt;gid&gt;:&lt;tid&gt;:&lt;srcId&gt;:&lt;rid&gt;/...</li>
	 * </ul>
	 * The source item itself (tvm3u:&lt;srcId&gt;) does not match.
	 */
	public static boolean belongsTo(String id, int srcId) {
		int field;

		if (id.startsWith(TvM3uGroupItem.SCHEME + ':') || id.startsWith(TvM3uKindItem.SCHEME + ':')) {
			field = 2;
		} else if (id.startsWith(TvM3uTrackItem.SCHEME + ':') || id.startsWith(TvM3uEpgItem.SCHEME + ':')) {
			field = 3;
		} else {
			return false;
		}

		int start = 0;

		for (int i = 0; i < field; i++) {
			start = id.indexOf(':', start) + 1;
			if (start == 0) return false;
		}

		int end = id.indexOf(':', start);
		if (end == -1) end = id.length();
		if (start == end) return false;
		String src = String.valueOf(srcId);
		return ((end - start) == src.length()) && id.startsWith(src, start);
	}
}
